#ifndef FLAC_FIXES_H
#define FLAC_FIXES_H

// Принудительное определение макросов минимума/максимума для старого кода libFLAC
#ifndef flac_min
#define flac_min(a,b) ((a)<(b)?(a):(b))
#endif

#ifndef flac_max
#define flac_max(a,b) ((a)>(b)?(a):(b))
#endif

#endif // FLAC_FIXES_H
