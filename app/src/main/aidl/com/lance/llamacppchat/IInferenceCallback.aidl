package com.lance.llamacppchat;

oneway interface IInferenceCallback {
    void onToken(String token);
    void onComplete();
    void onError(String message);
    void onModelLoading();
    void onModelReady();
}
