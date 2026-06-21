
#include <jni.h>
#include <stdbool.h>
#include "tbprobe.h"

/* int tbInitNative(String path) -> TB_LARGEST, or -1 on failure */
JNIEXPORT jint JNICALL
Java_SyzygyTablebase_tbInitNative(JNIEnv *env, jclass clazz, jstring jpath) {
    (void) clazz;
    if (jpath == NULL) {
        return -1;
    }
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    if (path == NULL) {
        return -1;
    }
    bool ok = tb_init(path);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return ok ? (jint) TB_LARGEST : (jint) -1;
}

/* void tbFreeNative() */
JNIEXPORT void JNICALL
Java_SyzygyTablebase_tbFreeNative(JNIEnv *env, jclass clazz) {
    (void) env; (void) clazz;
    tb_free();
}

/* int tbProbeWdlNative(...) -> 0..4 (loss..win), or -1 on failure.
 * Uses the public tb_probe_wdl wrapper with rule50=0 and castling=0.
 * Passing rule50=0 is safe because the engine's own 50-move-draw detection
 * runs before this probe is ever called, so by the time we get here we know
 * the position is not drawn by the 50-move rule. The public wrapper has
 * additional internal guards that prevent the lsb(0) assertion crash that
 * tb_probe_wdl_impl can trigger on pawnless/queenless positions. */
JNIEXPORT jint JNICALL
Java_SyzygyTablebase_tbProbeWdlNative(JNIEnv *env, jclass clazz,
        jlong white, jlong black, jlong kings, jlong queens,
        jlong rooks, jlong bishops, jlong knights, jlong pawns,
        jint ep, jboolean whiteToMove) {
    (void) env; (void) clazz;
    unsigned result = tb_probe_wdl(
            (uint64_t) white, (uint64_t) black, (uint64_t) kings, (uint64_t) queens,
            (uint64_t) rooks, (uint64_t) bishops, (uint64_t) knights, (uint64_t) pawns,
            0u /* rule50=0: engine handles 50-move draws before probing */,
            0u /* castling: TB positions have none */,
            (unsigned) ep, whiteToMove == JNI_TRUE);
    if (result == TB_RESULT_FAILED) {
        return (jint) -1;
    }
    return (jint) result;   /* TB_LOSS(0) .. TB_WIN(4) */
}

/* int[] tbProbeRootNative(...) -> {wdl, dtz, from, to, promo}, or {-1,0,0,0,0} */
JNIEXPORT jintArray JNICALL
Java_SyzygyTablebase_tbProbeRootNative(JNIEnv *env, jclass clazz,
        jlong white, jlong black, jlong kings, jlong queens,
        jlong rooks, jlong bishops, jlong knights, jlong pawns,
        jint rule50, jint ep, jboolean whiteToMove) {
    (void) clazz;
    unsigned result = tb_probe_root(
            (uint64_t) white, (uint64_t) black, (uint64_t) kings, (uint64_t) queens,
            (uint64_t) rooks, (uint64_t) bishops, (uint64_t) knights, (uint64_t) pawns,
            (unsigned) rule50, 0u /* castling: TB positions have none */,
            (unsigned) ep, whiteToMove == JNI_TRUE, NULL);

    jint vals[5];
    if (result == TB_RESULT_FAILED
            || result == TB_RESULT_CHECKMATE
            || result == TB_RESULT_STALEMATE) {
        vals[0] = -1; vals[1] = 0; vals[2] = 0; vals[3] = 0; vals[4] = 0;
    } else {
        vals[0] = (jint) TB_GET_WDL(result);
        vals[1] = (jint) TB_GET_DTZ(result);
        vals[2] = (jint) TB_GET_FROM(result);
        vals[3] = (jint) TB_GET_TO(result);
        vals[4] = (jint) TB_GET_PROMOTES(result);
    }
    jintArray out = (*env)->NewIntArray(env, 5);
    if (out != NULL) {
        (*env)->SetIntArrayRegion(env, out, 0, 5, vals);
    }
    return out;
}
