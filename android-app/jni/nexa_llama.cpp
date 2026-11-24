// NEXA Code 2100 — محرك الاستدلال المحلي: غلاف JNI حوالين llama.cpp
// بيحمّل نموذج GGUF وبيولّد منه نص (توليد جشع greedy زي إعدادات التدريب).
#include <jni.h>

#include <string>
#include <vector>

#include "llama.h"

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static const llama_vocab * g_vocab = nullptr;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_alex_nexacode_LocalModel_nativeInit(JNIEnv * env, jobject /*thiz*/,
                                             jstring modelPath, jint nCtx, jint nThreads) {
    if (g_model != nullptr && g_ctx != nullptr) return JNI_TRUE;
    const char * path = env->GetStringUTFChars(modelPath, nullptr);
    llama_backend_init();
    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);
    if (g_model == nullptr) return JNI_FALSE;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = (uint32_t) nCtx;
    cparams.n_batch = 1024;
    cparams.n_ubatch = 512;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;
    g_ctx = llama_init_from_model(g_model, cparams);
    if (g_ctx == nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }
    g_vocab = llama_model_get_vocab(g_model);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_alex_nexacode_LocalModel_nativeGenerate(JNIEnv * env, jobject /*thiz*/,
                                                 jstring prompt, jint maxTokens) {
    if (g_ctx == nullptr || g_vocab == nullptr) return env->NewStringUTF("");
    const char * p = env->GetStringUTFChars(prompt, nullptr);
    std::string text(p);
    env->ReleaseStringUTFChars(prompt, p);

    // محادثة جديدة كل مرة: نفضي ذاكرة الـ KV
    llama_memory_clear(llama_get_memory(g_ctx), true);

    // تحويل النص لتوكنز (مع تحليل التوكنز الخاصة زي <|im_start|>)
    int32_t n_needed = -llama_tokenize(g_vocab, text.c_str(), (int32_t) text.size(),
                                       nullptr, 0, true, true);
    if (n_needed <= 0) return env->NewStringUTF("");
    std::vector<llama_token> tokens((size_t) n_needed);
    llama_tokenize(g_vocab, text.c_str(), (int32_t) text.size(),
                   tokens.data(), (int32_t) tokens.size(), true, true);

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string out;
    llama_batch batch = llama_batch_get_one(tokens.data(), (int32_t) tokens.size());
    llama_token next = 0;
    for (int32_t i = 0; i < maxTokens; i++) {
        if (llama_decode(g_ctx, batch) != 0) break;
        next = llama_sampler_sample(sampler, g_ctx, -1);
        if (llama_vocab_is_eog(g_vocab, next)) break;
        char buf[256];
        int32_t m = llama_token_to_piece(g_vocab, next, buf, (int32_t) sizeof(buf), 0, true);
        if (m > 0) out.append(buf, (size_t) m);
        batch = llama_batch_get_one(&next, 1);
    }
    llama_sampler_free(sampler);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_alex_nexacode_LocalModel_nativeFree(JNIEnv * /*env*/, jobject /*thiz*/) {
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
}
