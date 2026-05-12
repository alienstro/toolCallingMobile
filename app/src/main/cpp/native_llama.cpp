#include <jni.h>

#include <android/log.h>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

#include "llama.h"

namespace {

constexpr const char *kLogTag = "NativeLlama";

struct NativeLlamaState {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    std::atomic_bool cancel_requested{false};
    std::atomic_bool release_requested{false};
    std::mutex mutex;
};

constexpr uint32_t kReplacementCodePoint = 0xFFFD;

std::mutex g_registry_mutex;
std::unordered_map<jlong, NativeLlamaState *> g_state_registry;
jlong g_next_handle = 1;
std::once_flag g_backend_once;

int64_t now_millis() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch())
            .count();
}

void log_duration(const char *label, int64_t started_at_ms) {
    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "%s took %lld ms",
            label,
            static_cast<long long>(now_millis() - started_at_ms));
}

class JniUtfChars {
public:
    JniUtfChars(JNIEnv *env, jstring value, const std::string &name)
            : env_(env), value_(value) {
        if (value_ == nullptr) {
            throw std::invalid_argument(name + " must not be null.");
        }

        chars_ = env_->GetStringUTFChars(value_, nullptr);
        if (chars_ == nullptr) {
            throw std::runtime_error("Failed to read " + name + ".");
        }
    }

    ~JniUtfChars() {
        if (chars_ != nullptr) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    JniUtfChars(const JniUtfChars &) = delete;
    JniUtfChars &operator=(const JniUtfChars &) = delete;

    const char *c_str() const {
        return chars_;
    }

private:
    JNIEnv *env_;
    jstring value_;
    const char *chars_ = nullptr;
};

class JniUtf16Chars {
public:
    JniUtf16Chars(JNIEnv *env, jstring value, const std::string &name)
            : env_(env), value_(value) {
        if (value_ == nullptr) {
            throw std::invalid_argument(name + " must not be null.");
        }

        length_ = env_->GetStringLength(value_);
        chars_ = env_->GetStringChars(value_, nullptr);
        if (chars_ == nullptr) {
            throw std::runtime_error("Failed to read " + name + ".");
        }
    }

    ~JniUtf16Chars() {
        if (chars_ != nullptr) {
            env_->ReleaseStringChars(value_, chars_);
        }
    }

    JniUtf16Chars(const JniUtf16Chars &) = delete;
    JniUtf16Chars &operator=(const JniUtf16Chars &) = delete;

    const jchar *data() const {
        return chars_;
    }

    jsize length() const {
        return length_;
    }

private:
    JNIEnv *env_;
    jstring value_;
    const jchar *chars_ = nullptr;
    jsize length_ = 0;
};

void append_utf8_code_point(std::string &output, uint32_t code_point) {
    if (code_point <= 0x7F) {
        output.push_back(static_cast<char>(code_point));
    } else if (code_point <= 0x7FF) {
        output.push_back(static_cast<char>(0xC0 | (code_point >> 6)));
        output.push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
    } else if (code_point <= 0xFFFF) {
        output.push_back(static_cast<char>(0xE0 | (code_point >> 12)));
        output.push_back(static_cast<char>(0x80 | ((code_point >> 6) & 0x3F)));
        output.push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
    } else {
        output.push_back(static_cast<char>(0xF0 | (code_point >> 18)));
        output.push_back(static_cast<char>(0x80 | ((code_point >> 12) & 0x3F)));
        output.push_back(static_cast<char>(0x80 | ((code_point >> 6) & 0x3F)));
        output.push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
    }
}

std::string utf16_to_utf8(const jchar *input, jsize length) {
    std::string output;
    output.reserve(static_cast<size_t>(length));

    for (jsize i = 0; i < length; ++i) {
        const uint32_t current = input[i];
        if (current >= 0xD800 && current <= 0xDBFF) {
            if (i + 1 < length) {
                const uint32_t next = input[i + 1];
                if (next >= 0xDC00 && next <= 0xDFFF) {
                    const uint32_t code_point =
                            0x10000 + (((current - 0xD800) << 10) | (next - 0xDC00));
                    append_utf8_code_point(output, code_point);
                    ++i;
                    continue;
                }
            }
            append_utf8_code_point(output, kReplacementCodePoint);
        } else if (current >= 0xDC00 && current <= 0xDFFF) {
            append_utf8_code_point(output, kReplacementCodePoint);
        } else {
            append_utf8_code_point(output, current);
        }
    }

    return output;
}

std::string java_string_to_utf8(JNIEnv *env, jstring value, const std::string &name) {
    const JniUtf16Chars chars(env, value, name);
    return utf16_to_utf8(chars.data(), chars.length());
}

void throw_illegal_state(JNIEnv *env, const std::string &message) {
    jclass clazz = env->FindClass("java/lang/IllegalStateException");
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message.c_str());
    }
}

NativeLlamaState *state_from_handle(jlong handle) {
    std::lock_guard<std::mutex> guard(g_registry_mutex);
    auto state = g_state_registry.find(handle);
    if (state == g_state_registry.end()) {
        throw std::runtime_error("llama.cpp model is not loaded.");
    }
    return state->second;
}

bool llama_should_abort(void *data) {
    auto *state = static_cast<NativeLlamaState *>(data);
    return state != nullptr &&
           (state->cancel_requested.load() || state->release_requested.load());
}

struct LockedState {
    NativeLlamaState *state;
    std::unique_lock<std::mutex> lock;
};

LockedState lock_state_from_handle(jlong handle) {
    std::lock_guard<std::mutex> registry_guard(g_registry_mutex);
    auto state = g_state_registry.find(handle);
    if (state == g_state_registry.end()) {
        throw std::runtime_error("llama.cpp model is not loaded.");
    }
    return LockedState{state->second, std::unique_lock<std::mutex>(state->second->mutex)};
}

void free_state(NativeLlamaState *state) {
    if (state == nullptr) {
        return;
    }
    if (state->context != nullptr) {
        llama_free(state->context);
        state->context = nullptr;
    }
    if (state->model != nullptr) {
        llama_model_free(state->model);
        state->model = nullptr;
    }
    delete state;
}

jlong register_state(NativeLlamaState *state) {
    std::lock_guard<std::mutex> guard(g_registry_mutex);
    const jlong handle = g_next_handle++;
    if (g_next_handle <= 0) {
        g_next_handle = 1;
    }
    g_state_registry[handle] = state;
    return handle;
}

NativeLlamaState *remove_state(jlong handle) {
    std::lock_guard<std::mutex> guard(g_registry_mutex);
    auto state = g_state_registry.find(handle);
    if (state == g_state_registry.end()) {
        throw std::runtime_error("llama.cpp model is not loaded.");
    }

    NativeLlamaState *removed = state->second;
    g_state_registry.erase(state);
    return removed;
}

LockedState remove_and_lock_state(jlong handle) {
    std::lock_guard<std::mutex> registry_guard(g_registry_mutex);
    auto state = g_state_registry.find(handle);
    if (state == g_state_registry.end()) {
        throw std::runtime_error("llama.cpp model is not loaded.");
    }

    NativeLlamaState *removed = state->second;
    removed->release_requested.store(true);
    removed->cancel_requested.store(true);
    g_state_registry.erase(state);
    return LockedState{removed, std::unique_lock<std::mutex>(removed->mutex)};
}

void request_cancel(jlong handle) {
    std::lock_guard<std::mutex> guard(g_registry_mutex);
    auto state = g_state_registry.find(handle);
    if (state == g_state_registry.end()) {
        throw std::runtime_error("llama.cpp model is not loaded.");
    }
    state->second->cancel_requested.store(true);
}

void validate_load_args(jint context_length, jint batch_size, jint threads) {
    if (context_length <= 0) {
        throw std::invalid_argument("context_length must be greater than 0.");
    }
    if (batch_size <= 0) {
        throw std::invalid_argument("batch_size must be greater than 0.");
    }
    if (threads <= 0) {
        throw std::invalid_argument("threads must be greater than 0.");
    }
}

jstring new_utf8_string(JNIEnv *env, const std::string &text) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    if (bytes == nullptr) {
        throw std::runtime_error("Failed to allocate token bytes.");
    }

    if (!text.empty()) {
        env->SetByteArrayRegion(
                bytes,
                0,
                static_cast<jsize>(text.size()),
                reinterpret_cast<const jbyte *>(text.data()));
        if (env->ExceptionCheck()) {
            env->DeleteLocalRef(bytes);
            throw std::runtime_error("Failed to copy token bytes.");
        }
    }

    jclass string_class = env->FindClass("java/lang/String");
    if (string_class == nullptr) {
        env->DeleteLocalRef(bytes);
        throw std::runtime_error("Missing java.lang.String.");
    }

    jmethodID constructor = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
    if (constructor == nullptr) {
        env->DeleteLocalRef(string_class);
        env->DeleteLocalRef(bytes);
        throw std::runtime_error("Missing String(byte[], String) constructor.");
    }

    jstring charset_name = env->NewStringUTF("UTF-8");
    if (charset_name == nullptr) {
        env->DeleteLocalRef(string_class);
        env->DeleteLocalRef(bytes);
        throw std::runtime_error("Failed to allocate UTF-8 charset name.");
    }

    auto result = static_cast<jstring>(
            env->NewObject(string_class, constructor, bytes, charset_name));

    env->DeleteLocalRef(charset_name);
    env->DeleteLocalRef(string_class);
    env->DeleteLocalRef(bytes);

    if (result == nullptr || env->ExceptionCheck()) {
        throw std::runtime_error("Failed to create Java token string.");
    }
    return result;
}

bool call_token_callback(
        JNIEnv *env,
        jobject callback,
        jmethodID on_token,
        const std::string &token) {
    jstring token_string = new_utf8_string(env, token);
    env->CallVoidMethod(callback, on_token, token_string);
    env->DeleteLocalRef(token_string);
    return !env->ExceptionCheck();
}

class Utf8StreamBuffer {
public:
    std::string append(const std::string &bytes) {
        pending_ += bytes;
        return consume(false);
    }

    std::string flush() {
        return consume(true);
    }

private:
    std::string pending_;

    std::string consume(bool flush_incomplete) {
        std::string output;
        size_t i = 0;

        while (i < pending_.size()) {
            const auto first = static_cast<uint8_t>(pending_[i]);
            if (first <= 0x7F) {
                output.push_back(pending_[i++]);
                continue;
            }

            int width = 0;
            uint32_t code_point = 0;
            uint32_t minimum = 0;
            if (first >= 0xC2 && first <= 0xDF) {
                width = 2;
                code_point = first & 0x1F;
                minimum = 0x80;
            } else if (first >= 0xE0 && first <= 0xEF) {
                width = 3;
                code_point = first & 0x0F;
                minimum = 0x800;
            } else if (first >= 0xF0 && first <= 0xF4) {
                width = 4;
                code_point = first & 0x07;
                minimum = 0x10000;
            } else {
                append_utf8_code_point(output, kReplacementCodePoint);
                ++i;
                continue;
            }

            bool valid = true;
            size_t invalid_consume = static_cast<size_t>(width);
            for (int j = 1; j < width; ++j) {
                if (i + static_cast<size_t>(j) >= pending_.size()) {
                    if (!flush_incomplete) {
                        if (i > 0) {
                            pending_.erase(0, i);
                        }
                        return output;
                    }
                    valid = false;
                    invalid_consume = pending_.size() - i;
                    break;
                }

                const auto continuation = static_cast<uint8_t>(pending_[i + j]);
                if ((continuation & 0xC0) != 0x80) {
                    valid = false;
                    invalid_consume = static_cast<size_t>(j);
                    break;
                }
                code_point = (code_point << 6) | (continuation & 0x3F);
            }

            if (!valid ||
                code_point < minimum ||
                code_point > 0x10FFFF ||
                (code_point >= 0xD800 && code_point <= 0xDFFF)) {
                append_utf8_code_point(output, kReplacementCodePoint);
                i += invalid_consume;
                continue;
            }

            output.append(pending_, i, static_cast<size_t>(width));
            i += static_cast<size_t>(width);
        }

        pending_.erase(0, i);
        return output;
    }
};

std::vector<llama_token> tokenize_prompt(const llama_vocab *vocab, const std::string &prompt) {
    const int32_t needed = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            nullptr,
            0,
            true,
            true);
    const int32_t token_count = needed < 0 ? -needed : needed;
    if (token_count <= 0) {
        throw std::runtime_error("Failed to tokenize prompt.");
    }

    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    const int32_t actual = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true);
    if (actual < 0) {
        throw std::runtime_error("Prompt token buffer was too small.");
    }
    tokens.resize(static_cast<size_t>(actual));
    return tokens;
}

bool decode_tokens(NativeLlamaState *state, std::vector<llama_token> &tokens) {
    llama_context *context = state->context;
    const int32_t batch_size = static_cast<int32_t>(llama_n_batch(context));
    if (batch_size <= 0) {
        throw std::runtime_error("Invalid llama.cpp batch size.");
    }

    int32_t offset = 0;
    while (offset < static_cast<int32_t>(tokens.size())) {
        if (state->cancel_requested.load() || state->release_requested.load()) {
            return false;
        }

        const int32_t remaining = static_cast<int32_t>(tokens.size()) - offset;
        const int32_t count = remaining < batch_size ? remaining : batch_size;
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, count);
        const int32_t decode_result = llama_decode(context, batch);
        if (state->cancel_requested.load() || state->release_requested.load()) {
            return false;
        }
        if (decode_result != 0) {
            throw std::runtime_error("Failed to evaluate prompt.");
        }
        offset += count;
    }
    return true;
}

std::string token_to_piece(const llama_vocab *vocab, llama_token token) {
    std::vector<char> buffer(128);
    int32_t length = llama_token_to_piece(
            vocab,
            token,
            buffer.data(),
            static_cast<int32_t>(buffer.size()),
            0,
            true);

    if (length < 0) {
        buffer.resize(static_cast<size_t>(-length));
        length = llama_token_to_piece(
                vocab,
                token,
                buffer.data(),
                static_cast<int32_t>(buffer.size()),
                0,
                true);
    }
    if (length < 0) {
        throw std::runtime_error("Failed to convert token to text.");
    }
    return std::string(buffer.data(), static_cast<size_t>(length));
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_lance_litertchat_inference_NativeLlamaBridge_nativeRuntimeVersion(
        JNIEnv *env,
        jobject /* thiz */) {
    return env->NewStringUTF("native-llamacpp");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_lance_litertchat_inference_NativeLlamaBridge_nativeLoadModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring model_path,
        jint context_length,
        jint batch_size,
        jint threads) {
    NativeLlamaState *state = nullptr;
    try {
        const int64_t started_at_ms = now_millis();
        validate_load_args(context_length, batch_size, threads);
        const JniUtfChars path_chars(env, model_path, "model_path");
        std::string path(path_chars.c_str());

        state = new NativeLlamaState();

        std::call_once(g_backend_once, []() {
            llama_backend_init();
        });

        llama_model_params model_params = llama_model_default_params();
        state->model = llama_model_load_from_file(path.c_str(), model_params);
        if (state->model == nullptr) {
            throw std::runtime_error("Failed to load GGUF model.");
        }

        llama_context_params context_params = llama_context_default_params();
        context_params.n_ctx = static_cast<uint32_t>(context_length);
        context_params.n_batch = static_cast<uint32_t>(batch_size);
        context_params.n_threads = threads;
        context_params.n_threads_batch = threads;
        context_params.abort_callback = llama_should_abort;
        context_params.abort_callback_data = state;

        state->context = llama_init_from_model(state->model, context_params);
        if (state->context == nullptr) {
            throw std::runtime_error("Failed to create llama.cpp context.");
        }
        llama_set_abort_callback(state->context, llama_should_abort, state);

        const jlong handle = register_state(state);
        state = nullptr;
        log_duration("model load", started_at_ms);
        return handle;
    } catch (const std::exception &error) {
        free_state(state);
        throw_illegal_state(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lance_litertchat_inference_NativeLlamaBridge_nativeGenerate(
        JNIEnv *env,
        jobject /* thiz */,
        jlong handle,
        jstring prompt,
        jint max_tokens,
        jfloat temperature,
        jint top_k,
        jfloat top_p,
        jobject callback) {
    try {
        const int64_t generation_started_at_ms = now_millis();
        if (max_tokens <= 0) {
            throw std::invalid_argument("max_tokens must be greater than 0.");
        }
        if (callback == nullptr) {
            throw std::invalid_argument("callback must not be null.");
        }

        std::string prompt_text = java_string_to_utf8(env, prompt, "prompt");

        LockedState locked_state = lock_state_from_handle(handle);
        NativeLlamaState *state = locked_state.state;
        if (state->release_requested.load()) {
            return new_utf8_string(env, "");
        }
        if (state->cancel_requested.exchange(false)) {
            return new_utf8_string(env, "");
        }

        jclass callback_class = env->GetObjectClass(callback);
        if (callback_class == nullptr) {
            throw std::runtime_error("Failed to read token callback.");
        }
        jmethodID on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(callback_class);
        if (on_token == nullptr) {
            throw std::runtime_error("Missing TokenCallback.onToken(String).");
        }

        const llama_vocab *vocab = llama_model_get_vocab(state->model);
        std::vector<llama_token> prompt_tokens = tokenize_prompt(vocab, prompt_text);
        if (prompt_tokens.size() >= llama_n_ctx(state->context)) {
            throw std::runtime_error("Prompt is too long for the configured context.");
        }

        llama_memory_clear(llama_get_memory(state->context), true);
        const int64_t prompt_eval_started_at_ms = now_millis();
        if (!decode_tokens(state, prompt_tokens)) {
            return new_utf8_string(env, "");
        }
        log_duration("prompt eval", prompt_eval_started_at_ms);

        llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
        std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
                llama_sampler_chain_init(sampler_params),
                llama_sampler_free);
        if (sampler == nullptr) {
            throw std::runtime_error("Failed to create llama.cpp sampler.");
        }
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        Utf8StreamBuffer stream_buffer;
        std::string output_bytes;
        int32_t generated = 0;
        uint32_t position = static_cast<uint32_t>(prompt_tokens.size());
        bool first_token_logged = false;

        while (generated < max_tokens &&
               !state->cancel_requested.load() &&
               !state->release_requested.load()) {
            if (position >= llama_n_ctx(state->context)) {
                break;
            }

            llama_token token = llama_sampler_sample(sampler.get(), state->context, -1);
            if (llama_vocab_is_eog(vocab, token)) {
                break;
            }

            std::string piece = token_to_piece(vocab, token);
            if (!first_token_logged) {
                log_duration("first token", generation_started_at_ms);
                first_token_logged = true;
            }
            if (!piece.empty()) {
                output_bytes += piece;
                std::string stream_text = stream_buffer.append(piece);
                if (!stream_text.empty()) {
                    if (!call_token_callback(env, callback, on_token, stream_text)) {
                        return nullptr;
                    }
                }
            }

            llama_batch next_batch = llama_batch_get_one(&token, 1);
            const int32_t decode_result = llama_decode(state->context, next_batch);
            if (state->cancel_requested.load() || state->release_requested.load()) {
                break;
            }
            if (decode_result != 0) {
                throw std::runtime_error("Failed to evaluate generated token.");
            }

            ++generated;
            ++position;
        }

        const std::string final_stream_text = stream_buffer.flush();
        if (!final_stream_text.empty()) {
            if (!call_token_callback(env, callback, on_token, final_stream_text)) {
                return nullptr;
            }
        }

        __android_log_print(
                ANDROID_LOG_INFO,
                kLogTag,
                "generation produced %d tokens in %lld ms",
                generated,
                static_cast<long long>(now_millis() - generation_started_at_ms));
        return new_utf8_string(env, output_bytes);
    } catch (const std::exception &error) {
        if (!env->ExceptionCheck()) {
            throw_illegal_state(env, error.what());
        }
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_lance_litertchat_inference_NativeLlamaBridge_nativeCancel(
        JNIEnv *env,
        jobject /* thiz */,
        jlong handle) {
    try {
        request_cancel(handle);
    } catch (const std::exception &error) {
        throw_illegal_state(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_lance_litertchat_inference_NativeLlamaBridge_nativeRelease(
        JNIEnv *env,
        jobject /* thiz */,
        jlong handle) {
    try {
        LockedState locked_state = remove_and_lock_state(handle);
        locked_state.lock.unlock();
        free_state(locked_state.state);
    } catch (const std::exception &error) {
        throw_illegal_state(env, error.what());
    }
}
