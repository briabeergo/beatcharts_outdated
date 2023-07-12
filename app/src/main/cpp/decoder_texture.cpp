//
// File created by briabeergo on 11.08.2022. Decoding code by Perfare (https://github.com/Perfare/AssetStudio)
//

#include <jni.h>
#include <iostream>

#include <cstdint>
#include <cstring>
#include <string>
#include <fstream>
#include <vector>
#include <android/log.h>
#include "rg_etc1.h"

#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, "testnative", __VA_ARGS__)

using namespace std;
using namespace rg_etc1;

uint_fast8_t clamp(int n);
uint_fast32_t color(uint8_t r, uint8_t g, uint8_t b, uint8_t a);
uint32_t applicate_color(uint_fast8_t c[3], int_fast16_t m);
void copy_block_buffer(long bx, long by, long w, long h, long bw, long bh, const uint32_t *buffer, uint32_t *image);
static void decode_etc1_block(const uint8_t *data, uint32_t *outbuf);
void decode_etc1(const uint8_t *data, uint32_t *image, int w, int h);
jbyteArray uint8ArrayToJbyteArray(JNIEnv* env, uint8_t* array, int size);

extern "C" JNIEXPORT jbyteArray JNICALL
Java_ru_acted_beatcharts_DeEncodingManager_decodeBytesC(JNIEnv *env, jobject thiz, jbyteArray dataInput, jint dataInputSize, jint w, jint h) {

    uint32_t image[w * h * 4];

    uint8_t data[dataInputSize];
    jbyte *bytes = (*env).GetByteArrayElements(dataInput, nullptr);
    for (int i = 0; i < dataInputSize; i++){
        data[i] = bytes[i];
    }

    decode_etc1(data, image, w, h);

    jbyteArray jDecodedImage = env->NewByteArray(sizeof(image));
    env->SetByteArrayRegion(jDecodedImage, 0, sizeof(image), reinterpret_cast<const jbyte *>(image));

    return jDecodedImage;
}

uint_fast8_t clamp(const int n) {
    return n < 0 ? 0 : n > 255 ? 255 : n;
}

uint_fast32_t color(uint8_t r, uint8_t g, uint8_t b, uint8_t a) {
     return r | g << 8 | b << 16 | a << 24; //ALPHA IS FIRST HERE
}
uint32_t applicate_color(uint_fast8_t c[3], int_fast16_t m) {
    return color(clamp(c[0] + m), clamp(c[1] + m), clamp(c[2] + m), 255);
}
void copy_block_buffer(const long bx, const long by, const long w, const long h, const long bw, const long bh, const uint32_t *buffer, uint32_t *image) {
    long x = bw * bx;
    long xl = (bw * (bx + 1) > w ? w - bw * bx : bw) * 4;
    const uint32_t *buffer_end = buffer + bw * bh;
    for (long y = by * bh; buffer < buffer_end && y < h; buffer += bw, y++){
        memcpy(image + y * w + x, buffer, xl);
    }
}

static void decode_etc1_block(const uint8_t *data, uint32_t *outbuf) {

    uint_fast8_t WriteOrderTable[16] = {0, 4, 8, 12, 1, 5, 9, 13, 2, 6, 10, 14, 3, 7, 11, 15};
    uint_fast8_t Etc1ModifierTable[8][2] = {{2,  8}, {5, 17}, {9, 29}, {13, 42},
                                            {18, 60}, {24, 80}, {33, 106}, {47, 183}};
    uint_fast8_t Etc1SubblockTable2[2][16] = {{0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1},
                                              {0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1}};
    uint_fast8_t code[2] = {static_cast<uint_fast8_t>(data[3] >> 5), static_cast<uint_fast8_t>(data[3] >> 2 & 7)};  // Table codewords
    const uint_fast8_t *table = Etc1SubblockTable2[data[3] & 1];
    uint_fast8_t c[2][3];
    if (data[3] & 2) {
        // diff bit == 1
        c[0][0] = data[0] & 0xf8;
        c[0][1] = data[1] & 0xf8;
        c[0][2] = data[2] & 0xf8;
        c[1][0] = c[0][0] + (data[0] << 3 & 0x18) - (data[0] << 3 & 0x20);
        c[1][1] = c[0][1] + (data[1] << 3 & 0x18) - (data[1] << 3 & 0x20);
        c[1][2] = c[0][2] + (data[2] << 3 & 0x18) - (data[2] << 3 & 0x20);
        c[0][0] |= c[0][0] >> 5;
        c[0][1] |= c[0][1] >> 5;
        c[0][2] |= c[0][2] >> 5;
        c[1][0] |= c[1][0] >> 5;
        c[1][1] |= c[1][1] >> 5;
        c[1][2] |= c[1][2] >> 5;
    } else {
        // diff bit == 0
        c[0][0] = (data[0] & 0xf0) | data[0] >> 4;
        c[1][0] = (data[0] & 0x0f) | data[0] << 4;
        c[0][1] = (data[1] & 0xf0) | data[1] >> 4;
        c[1][1] = (data[1] & 0x0f) | data[1] << 4;
        c[0][2] = (data[2] & 0xf0) | data[2] >> 4;
        c[1][2] = (data[2] & 0x0f) | data[2] << 4;
    }

    uint_fast16_t j = data[6] << 8 | data[7];  // less significant pixel index bits
    uint_fast16_t k = data[4] << 8 | data[5];  // more significant pixel index bits
    for (int i = 0; i < 16; i++, j >>= 1, k >>= 1) {
        uint_fast8_t s = table[i];
        uint_fast8_t m = Etc1ModifierTable[code[s]][j & 1];
        outbuf[WriteOrderTable[i]] = applicate_color(c[s], k & 1 ? -m : m);
    }
}
void decode_etc1(const uint8_t *data, uint32_t *image, int w, int h) {
    long num_blocks_x = (w + 3) / 4;
    long num_blocks_y = (h + 3) / 4;
    uint32_t buffer[16];
    for (long by = 0; by < num_blocks_y; by++) {
        for (long bx = 0; bx < num_blocks_x; bx++, data += 8) {
            decode_etc1_block(data, buffer);
            copy_block_buffer(bx, by, w, h, 4, 4, buffer, image);
        }
    }
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_ru_acted_beatcharts_DeEncodingManager_encodeArtworkBytes(JNIEnv *env, jobject thiz, jbyteArray dataInput, jint dataInputSize) {
    //Convert java array to c++ array (new array is "data")

    // Создаем массив uint32_t нужного размера
    int size = env->GetArrayLength(dataInput);
    uint32_t* dataOriginal = new uint32_t[size/4];
    // Получаем указатель на элементы jbyteArray
    jbyte* bytes = env->GetByteArrayElements(dataInput, nullptr);
    // Копируем байты в массив uint32_t по четыре за раз
    memcpy(dataOriginal, bytes, size);
    // Освобождаем ресурсы
    env->ReleaseByteArrayElements(dataInput, bytes, 0);

    /*uint8_t data[dataInputSize];
    jbyte *bytes = (*env).GetByteArrayElements(dataInput, nullptr);
    for (int i = 0; i < dataInputSize; i++){
        data[i] = bytes[i];
    }*/

    pack_etc1_block_init();

    auto params = new etc1_pack_params;
    params->m_dithering = false;
    params->m_quality = cMediumQuality;

    uint8_t encoded[512 * 512] = {};

    int h = 512;
    int w = 512;

    int blockCopyStep = 0;
    for (int i = 0; i < h; i+=4) { //Height
        for (int j = 0; j < w; j+=4) { //Width

            unsigned int currentBlock[4][4];
            uint8_t encodedBlock[8] = {};

            //First row
            currentBlock[0][0] = dataOriginal[(i * w) + j];
            currentBlock[0][1] = dataOriginal[(i * w) + j + 1];
            currentBlock[0][2] = dataOriginal[(i * w) + j + 2];
            currentBlock[0][3] = dataOriginal[(i * w) + j + 3];
            //Second row
            currentBlock[1][0] = dataOriginal[((i + 1) * w) + j];
            currentBlock[1][1] = dataOriginal[((i + 1) * w) + j + 1];
            currentBlock[1][2] = dataOriginal[((i + 1) * w) + j + 2];
            currentBlock[1][3] = dataOriginal[((i + 1) * w) + j + 3];
            //Third row
            currentBlock[2][0] = dataOriginal[((i + 2) * w) + j];
            currentBlock[2][1] = dataOriginal[((i + 2) * w) + j + 1];
            currentBlock[2][2] = dataOriginal[((i + 2) * w) + j + 2];
            currentBlock[2][3] = dataOriginal[((i + 2) * w) + j + 3];
            //Fourth row
            currentBlock[3][0] = dataOriginal[((i + 3) * w) + j];
            currentBlock[3][1] = dataOriginal[((i + 3) * w) + j + 1];
            currentBlock[3][2] = dataOriginal[((i + 3) * w) + j + 2];
            currentBlock[3][3] = dataOriginal[((i + 3) * w) + j + 3];

            pack_etc1_block(encodedBlock, &currentBlock[0][0], *params);

            cout << i << " " << j << " | " << ((i / 4) * 128) + (j / 4) << endl;

            copy(encodedBlock, encodedBlock + 8, encoded + blockCopyStep);
            blockCopyStep += 8;
        }
    }

    /*jbyteArray jEncodedImage = env->NewByteArray(sizeof(encoded));
    env->SetByteArrayRegion(jEncodedImage, 0, sizeof(encoded), reinterpret_cast<const jbyte *>(encoded));*/

    /*ofstream foutenc;
    foutenc.open(R"(/storage/emulated/0/beatstar/BCTests/textureC.dat)", ios::binary | ios::out);
    foutenc.write((char*)&encoded, sizeof(encoded));
    foutenc.close();*/

    return uint8ArrayToJbyteArray(env, encoded, 131072);
}

jbyteArray uint8ArrayToJbyteArray(JNIEnv* env, uint8_t* array, int size) {
    // Создаем новый объект jbyteArray с заданным размером
    jbyteArray result = env->NewByteArray(size);
    // Устанавливаем элементы jbyteArray с помощью данных из uint8_t массива
    env->SetByteArrayRegion(result, 0, size, (jbyte*)array);
    // Возвращаем результат
    return result;
}