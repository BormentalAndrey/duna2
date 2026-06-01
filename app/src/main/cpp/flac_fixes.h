#ifndef FLAC_FIXES_H
#define FLAC_FIXES_H

// 1. Фикс для format.c: задаем версию библиотеки FLAC
#ifndef PACKAGE_VERSION
#define PACKAGE_VERSION "1.3.2"
#endif

// 2. Фикс для lpc.c: сообщаем FLAC, что в Android уже есть системный lround
#ifndef HAVE_LROUND
#define HAVE_LROUND 1
#endif
#ifndef HAVE_LROUNDF
#define HAVE_LROUNDF 1
#endif

// 3. Предыдущие фиксы для вычисления минимума/максимума
#ifndef flac_min
#define flac_min(a,b) ((a)<(b)?(a):(b))
#endif

#ifndef flac_max
#define flac_max(a,b) ((a)>(b)?(a):(b))
#endif

#endif // FLAC_FIXES_H
