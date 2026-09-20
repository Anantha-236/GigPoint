#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <string.h>

#include "whisper.h"

#define TAG "DhwaniWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static size_t asset_read(
        void *ctx,
        void *output,
        size_t read_size
) {
    return AAsset_read(
            (AAsset *) ctx,
            output,
            read_size
    );
}

static bool asset_eof(
        void *ctx
) {
    return AAsset_getRemainingLength64(
            (AAsset *) ctx
    ) <= 0;
}

static void asset_close(
        void *ctx
) {
    AAsset_close(
            (AAsset *) ctx
    );
}

static struct whisper_context *init_from_asset(
        JNIEnv *env,
        jobject asset_manager_java,
        const char *asset_path
) {

    AAssetManager *asset_manager =
            AAssetManager_fromJava(
                    env,
                    asset_manager_java
            );

    if (
        asset_manager ==
        NULL
    ) {

        LOGE(
                "Could not obtain Android AssetManager"
        );

        return NULL;
    }

    AAsset *asset =
            AAssetManager_open(
                    asset_manager,
                    asset_path,
                    AASSET_MODE_STREAMING
            );

    if (
        asset ==
        NULL
    ) {

        LOGE(
                "Could not open model asset: %s",
                asset_path
        );

        return NULL;
    }

    struct whisper_model_loader loader = {
            .context = asset,
            .read = asset_read,
            .eof = asset_eof,
            .close = asset_close
    };

    return whisper_init_with_params(
            &loader,
            whisper_context_default_params()
    );
}

JNIEXPORT jlong JNICALL
Java_com_example_gigpoint_voice_WhisperNative_initContextFromAsset(
        JNIEnv *env,
        jobject thiz,
        jobject asset_manager,
        jstring asset_path
) {

    (void) thiz;

    const char *path =
            (*env)->GetStringUTFChars(
                    env,
                    asset_path,
                    NULL
            );

    struct whisper_context *ctx =
            init_from_asset(
                    env,
                    asset_manager,
                    path
            );

    (*env)->ReleaseStringUTFChars(
            env,
            asset_path,
            path
    );

    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_com_example_gigpoint_voice_WhisperNative_freeContext(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr
) {

    (void) env;
    (void) thiz;

    struct whisper_context *ctx =
            (struct whisper_context *)
            context_ptr;

    if (
        ctx !=
        NULL
    ) {

        whisper_free(
                ctx
        );
    }
}

JNIEXPORT jint JNICALL
Java_com_example_gigpoint_voice_WhisperNative_fullTranscribe(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jint num_threads,
        jfloatArray audio_data,
        jstring language,
        jstring initial_prompt
) {

    (void) thiz;

    struct whisper_context *ctx =
            (struct whisper_context *)
            context_ptr;

    if (
        ctx ==
        NULL
    ) {
        return -10;
    }

    jfloat *samples =
            (*env)->GetFloatArrayElements(
                    env,
                    audio_data,
                    NULL
            );

    const jsize sample_count =
            (*env)->GetArrayLength(
                    env,
                    audio_data
            );

    const char *language_chars =
            (*env)->GetStringUTFChars(
                    env,
                    language,
                    NULL
            );

    const char *prompt_chars =
            NULL;

    if (
        initial_prompt !=
        NULL
    ) {

        prompt_chars =
                (*env)->GetStringUTFChars(
                        env,
                        initial_prompt,
                        NULL
                );
    }

    struct whisper_full_params params =
            whisper_full_default_params(
                    WHISPER_SAMPLING_GREEDY
            );

    params.print_realtime =
            false;

    params.print_progress =
            false;

    params.print_timestamps =
            false;

    params.print_special =
            false;

    params.translate =
            false;

    /**
     * IMPORTANT:
     *
     * "auto" is a valid language value and makes Whisper choose the
     * primary spoken language automatically.
     *
     * detect_language=true has a different purpose in whisper.cpp:
     * it is used by the explicit "detect language" operation. Enabling
     * it here can stop the normal full-transcription workflow.
     *
     * Therefore DhwaniMitra keeps detect_language=false and passes
     * language="auto" for normal multilingual transcription.
     */
    params.language =
            language_chars;

    params.detect_language =
            false;

    params.n_threads =
            num_threads;

    // Merchant commands are independent short utterances.
    params.no_context =
            true;

    params.single_segment =
            true;

    if (
        prompt_chars !=
        NULL &&
        strlen(
                prompt_chars
        ) >
        0
    ) {

        params.initial_prompt =
                prompt_chars;
    }

    whisper_reset_timings(
            ctx
    );

    const int result =
            whisper_full(
                    ctx,
                    params,
                    samples,
                    sample_count
            );

    if (
        prompt_chars !=
        NULL
    ) {

        (*env)->ReleaseStringUTFChars(
                env,
                initial_prompt,
                prompt_chars
        );
    }

    (*env)->ReleaseStringUTFChars(
            env,
            language,
            language_chars
    );

    (*env)->ReleaseFloatArrayElements(
            env,
            audio_data,
            samples,
            JNI_ABORT
    );

    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_gigpoint_voice_WhisperNative_getSegmentCount(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr
) {

    (void) env;
    (void) thiz;

    struct whisper_context *ctx =
            (struct whisper_context *)
            context_ptr;

    if (
        ctx ==
        NULL
    ) {
        return 0;
    }

    return whisper_full_n_segments(
            ctx
    );
}

JNIEXPORT jstring JNICALL
Java_com_example_gigpoint_voice_WhisperNative_getSegmentText(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jint index
) {

    (void) thiz;

    struct whisper_context *ctx =
            (struct whisper_context *)
            context_ptr;

    if (
        ctx ==
        NULL
    ) {

        return (*env)->NewStringUTF(
                env,
                ""
        );
    }

    const char *text =
            whisper_full_get_segment_text(
                    ctx,
                    index
            );

    return (*env)->NewStringUTF(
            env,
            text != NULL
                ? text
                : ""
    );
}

JNIEXPORT jstring JNICALL
Java_com_example_gigpoint_voice_WhisperNative_getDetectedLanguage(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr
) {

    (void) thiz;

    struct whisper_context *ctx =
            (struct whisper_context *)
            context_ptr;

    if (
        ctx ==
        NULL
    ) {

        return (*env)->NewStringUTF(
                env,
                ""
        );
    }

    const int language_id =
            whisper_full_lang_id(
                    ctx
            );

    if (
        language_id <
        0
    ) {

        return (*env)->NewStringUTF(
                env,
                ""
        );
    }

    const char *language =
            whisper_lang_str(
                    language_id
            );

    return (*env)->NewStringUTF(
            env,
            language != NULL
                ? language
                : ""
    );
}
