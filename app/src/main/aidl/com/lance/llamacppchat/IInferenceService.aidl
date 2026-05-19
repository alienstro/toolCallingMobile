package com.lance.llamacppchat;

import com.lance.llamacppchat.IInferenceCallback;

interface IInferenceService {
    void generate(String prompt, IInferenceCallback callback);
    void cancel();
    boolean isModelLoaded();
    boolean isBusy();
}
