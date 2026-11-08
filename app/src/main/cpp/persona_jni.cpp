#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstring>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "persona"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model       * g_model   = nullptr;
static llama_context     * g_ctx     = nullptr;
static const llama_vocab * g_vocab   = nullptr;
static int                 g_n_ctx   = 0;
static int                 g_threads = 0;

static constexpr int PROMPT_BATCH = 512;

static void log_callback(ggml_log_level level, const char * text, void * /*ud*/) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        LOGE("%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        LOGW("%s", text);
    } else {
        LOGI("%s", text);
    }
}

/** 返回 s 中完整 UTF-8 序列的字节数（尾部被截断的多字节字符不算）。 */
static size_t utf8_complete_len(const std::string & s) {
    size_t i = 0;
    while (i < s.size()) {
        const unsigned char c = static_cast<unsigned char>(s[i]);
        size_t need;
        if (c < 0x80)              need = 1;
        else if ((c & 0xE0) == 0xC0) need = 2;
        else if ((c & 0xF0) == 0xE0) need = 3;
        else if ((c & 0xF8) == 0xF0) need = 4;
        else return i;

        if (i + need > s.size()) break;
        bool ok = true;
        for (size_t k = 1; k < need; ++k) {
            if ((static_cast<unsigned char>(s[i + k]) & 0xC0) != 0x80) { ok = false; break; }
        }
        if (!ok) return i;
        i += need;
    }
    return i;
}

static void release_context() {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    g_n_ctx = 0;
    g_threads = 0;
}

static bool ensure_context(int n_ctx, int threads) {
    if (g_ctx && g_n_ctx == n_ctx && g_threads == threads) return true;
    release_context();

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx          = static_cast<uint32_t>(n_ctx);
    cp.n_batch        = PROMPT_BATCH;
    cp.n_ubatch       = PROMPT_BATCH;
    cp.n_threads      = threads;
    cp.n_threads_batch = threads;

    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) {
        LOGE("llama_init_from_model failed (n_ctx=%d)", n_ctx);
        return false;
    }
    g_n_ctx = n_ctx;
    g_threads = threads;
    return true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_localpersona_app_llm_LlamaBridge_nativeInit(JNIEnv * /*env*/, jobject /*thiz*/) {
    llama_log_set(log_callback, nullptr);
    llama_backend_init();
    LOGI("backend initialized");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_localpersona_app_llm_LlamaBridge_nativeLoadModel(JNIEnv * env, jobject /*thiz*/, jstring jpath) {
    if (g_model) return 0;

    const char * path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return -1;

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    // v0.9.0 起 mmap 由 load_mode 控制，默认即 mmap，无需单独设置

    LOGI("loading model: %s", path);
    llama_model * model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);

    if (!model) {
        LOGE("failed to load model");
        return 1;
    }

    g_model = model;
    g_vocab = llama_model_get_vocab(model);
    LOGI("model loaded");
    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_localpersona_app_llm_LlamaBridge_nativeIsLoaded(JNIEnv * /*env*/, jobject /*thiz*/) {
    return g_model != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_localpersona_app_llm_LlamaBridge_nativeFreeModel(JNIEnv * /*env*/, jobject /*thiz*/) {
    release_context();
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_localpersona_app_llm_LlamaBridge_nativeSystemInfo(JNIEnv * env, jobject /*thiz*/) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_localpersona_app_llm_LlamaBridge_nativeGenerate(
        JNIEnv * env,
        jobject /*thiz*/,
        jstring jprompt,
        jint max_tokens,
        jfloat temperature,
        jfloat top_p,
        jint top_k,
        jfloat min_p,
        jint threads,
        jint n_ctx,
        jobject sink) {

    if (!g_model || !g_vocab) return -2;
    if (!ensure_context(n_ctx, std::max(1, static_cast<int>(threads)))) return -3;

    llama_memory_clear(llama_get_memory(g_ctx), true);

    const char * prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (!prompt) return -1;
    const int prompt_len = static_cast<int>(strlen(prompt));

    int n_tokens = -llama_tokenize(g_vocab, prompt, prompt_len, nullptr, 0, false, true);
    if (n_tokens <= 0) {
        env->ReleaseStringUTFChars(jprompt, prompt);
        return -4;
    }
    std::vector<llama_token> tokens(n_tokens);
    const int got = llama_tokenize(g_vocab, prompt, prompt_len, tokens.data(), n_tokens, false, true);
    env->ReleaseStringUTFChars(jprompt, prompt);
    if (got <= 0) return -5;

    LOGI("prompt tokens: %d", got);

    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler * smpl = llama_sampler_chain_init(sparams);
    if (top_k > 0)              llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
    if (top_p > 0.0f)           llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    if (min_p > 0.0f)           llama_sampler_chain_add(smpl, llama_sampler_init_min_p(min_p, 1));
    if (temperature > 0.0f)     llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_batch batch = llama_batch_init(PROMPT_BATCH, 0, 1);

    jclass sink_cls = env->GetObjectClass(sink);
    jmethodID on_token = env->GetMethodID(sink_cls, "onToken", "(Ljava/lang/String;)Z");
    if (!on_token) {
        llama_batch_free(batch);
        llama_sampler_free(smpl);
        return -6;
    }

    int rc = 0;

    // ---- 预填 ----
    for (int i = 0; i < got; i += PROMPT_BATCH) {
        const int cur = std::min(PROMPT_BATCH, got - i);
        batch.n_tokens = cur;
        for (int j = 0; j < cur; ++j) {
            batch.token[j]   = tokens[i + j];
            batch.pos[j]     = i + j;
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j]  = (i + j == got - 1) ? 1 : 0;
        }
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("prompt decode failed at %d", i);
            rc = -7;
            break;
        }
    }

    // ---- 逐 token 生成 ----
    if (rc == 0) {
        std::string pending;
        int n_past = got;
        bool aborted = false;

        for (int i = 0; i < max_tokens; ++i) {
            const llama_token id = llama_sampler_sample(smpl, g_ctx, -1);
            if (llama_vocab_is_eog(g_vocab, id)) break;

            char buf[512];
            const int len = llama_token_to_piece(g_vocab, id, buf, sizeof(buf), 0, false);
            if (len > 0) {
                pending.append(buf, static_cast<size_t>(len));
                const size_t complete = utf8_complete_len(pending);
                if (complete > 0) {
                    const std::string piece = pending.substr(0, complete);
                    jstring js = env->NewStringUTF(piece.c_str());
                    const jboolean keep = env->CallBooleanMethod(sink, on_token, js);
                    env->DeleteLocalRef(js);
                    if (env->ExceptionCheck()) {
                        env->ExceptionClear();
                        aborted = true;
                        break;
                    }
                    pending.erase(0, complete);
                    if (!keep) { aborted = true; break; }
                }
            }

            batch.n_tokens     = 1;
            batch.token[0]     = id;
            batch.pos[0]       = n_past++;
            batch.n_seq_id[0]  = 1;
            batch.seq_id[0][0] = 0;
            batch.logits[0]    = 1;

            if (llama_decode(g_ctx, batch) != 0) {
                LOGE("decode failed during generation");
                break;
            }
        }
        if (aborted) rc = 1;
    }

    llama_batch_free(batch);
    llama_sampler_free(smpl);
    return rc;
}
