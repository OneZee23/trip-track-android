// iOS: FogFrameEncoder.swift (visible chunks). Host-only seam for the actual native geometry:
// JVM tests exercise fog_frame.hpp without loading Android's GLES/JNI library.
#include "fog_frame.hpp"
#include <cstdlib>
#include <iomanip>
#include <iostream>

int main(int argc, char** argv) {
    using namespace triptrack::fog;
    std::cout << std::setprecision(17);
    if (argc == 3) {
        std::cout << nearestWorldX(std::strtod(argv[1], nullptr), std::strtod(argv[2], nullptr));
        return 0;
    }
    if (argc != 6) return 1;
    auto copies = visibleWorldCopies(std::strtod(argv[1], nullptr), std::strtod(argv[2], nullptr),
                                    std::strtod(argv[3], nullptr), std::strtod(argv[4], nullptr),
                                    std::strtod(argv[5], nullptr));
    std::cout << copies.first << ' ' << copies.last;
}
