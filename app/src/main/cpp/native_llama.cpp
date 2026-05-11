#include <jni.h>

#include <atomic>
#include <cstdint>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>

#include "llama.h"

namespace {

struct NativeLlamaState {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    std::atomic_bool cancel_requested{false};
    std::mutex mutex;
};

std::mutex g_registry_mutex;
std::unordered_map<jlong, NativeLlamaState *> g_state_registry;
jlong g_next_handle = 1;
std::once_flag g_backend_once;

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

        state->context = llama_init_from_model(state->model, context_params);
        if (state->context == nullptr) {
            throw std::runtime_error("Failed to create llama.cpp context.");
        }

        const jlong handle = register_state(state);
        state = nullptr;
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
        jlong /* handle */,
        jstring /* prompt */,
        jint /* max_tokens */,
        jfloat /* temperature */,
        jint /* top_k */,
        jfloat /* top_p */,
        jobject /* callback */) {
    throw_illegal_state(env, "Native generation is not implemented yet.");
    return env->NewStringUTF("");
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
        free_state(remove_state(handle));
    } catch (const std::exception &error) {
        throw_illegal_state(env, error.what());
    }
}
