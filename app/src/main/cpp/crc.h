//
// File created by BriaBeerGo on 01.06.2023. CODE FROM PROJECT Audiokinetic Wwise RIFF/RIFX Vorbis to Ogg Vorbis converter BY hcs!! hcs is genius... [https://github.com/hcs64/ww2ogg]
//
#ifndef _CRC_H
#define _CRC_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

uint32_t checksum(unsigned char *data, int bytes);

#ifdef __cplusplus
}
#endif

#endif
