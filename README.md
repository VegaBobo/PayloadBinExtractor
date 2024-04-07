## Kotlin PayloadBinExtractor
Another Android OTAs payload.bin extractor, now in Kotlin.

I have been testing some personal Custom ROM builds on my main device with DSU, and would be really conventional if DSU Sideloader supports Custom ROM zips directly, so i decided to learn how payload.bin extraction works, and this project has born.

Keep in mind that this payload.bin extractor wasn't tested a lot, it is likely slow compared to others, and was really made as an learning project, if you need a reliable tool, i highly recommend checking other payload.bin extractors.

## Usage

    java -jar PayloadBinExtractor.jar path/to/payload.bin <optional: output dir>

## Building
You can build this project by:
1. git clone this repo
2. ./gradlew jar
3. check output jar at build/libs

If you don't want to build it yourself, there is also a ready to use jar, in prebuilt folder.

