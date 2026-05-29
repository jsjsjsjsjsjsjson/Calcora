cmake_minimum_required(VERSION 3.22.1)
project(calcora)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(GIAC_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../giac-2.0.0")
set(GIAC_SRC "${GIAC_SOURCE_DIR}/src")
set(LIBTOMMATH_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/libtommath-1.3.0")

configure_file("${GIAC_SRC}/config.h.android" "${GIAC_SRC}/config.h" COPYONLY)
configure_file("${GIAC_SRC}/config.h.android" "${GIAC_SOURCE_DIR}/config.h" COPYONLY)

set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
add_subdirectory("${LIBTOMMATH_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/libtommath")

set(GIAC_CORE_SOURCES
        ${GIAC_SRC}/input_lexer.cc
        ${GIAC_SRC}/sym2poly.cc
        ${GIAC_SRC}/gausspol.cc
        ${GIAC_SRC}/threaded.cc
        ${GIAC_SRC}/moyal.cc
        ${GIAC_SRC}/maple.cc
        ${GIAC_SRC}/ti89.cc
        ${GIAC_SRC}/mathml.cc
        ${GIAC_SRC}/misc.cc
        ${GIAC_SRC}/permu.cc
        ${GIAC_SRC}/quater.cc
        ${GIAC_SRC}/desolve.cc
        ${GIAC_SRC}/input_parser.cc
        ${GIAC_SRC}/symbolic.cc
        ${GIAC_SRC}/index.cc
        ${GIAC_SRC}/modpoly.cc
        ${GIAC_SRC}/modfactor.cc
        ${GIAC_SRC}/ezgcd.cc
        ${GIAC_SRC}/derive.cc
        ${GIAC_SRC}/solve.cc
        ${GIAC_SRC}/intg.cc
        ${GIAC_SRC}/intgab.cc
        ${GIAC_SRC}/risch.cc
        ${GIAC_SRC}/lin.cc
        ${GIAC_SRC}/series.cc
        ${GIAC_SRC}/subst.cc
        ${GIAC_SRC}/vecteur.cc
        ${GIAC_SRC}/sparse.cc
        ${GIAC_SRC}/csturm.cc
        ${GIAC_SRC}/tex.cc
        ${GIAC_SRC}/global.cc
        ${GIAC_SRC}/ifactor.cc
        ${GIAC_SRC}/alg_ext.cc
        ${GIAC_SRC}/gauss.cc
        ${GIAC_SRC}/isom.cc
        ${GIAC_SRC}/help.cc
        ${GIAC_SRC}/plot.cc
        ${GIAC_SRC}/plot3d.cc
        ${GIAC_SRC}/rpn.cc
        ${GIAC_SRC}/prog.cc
        ${GIAC_SRC}/pari.cc
        ${GIAC_SRC}/cocoa.cc
        ${GIAC_SRC}/usual.cc
        ${GIAC_SRC}/identificateur.cc
        ${GIAC_SRC}/gen.cc
        ${GIAC_SRC}/unary.cc
        ${GIAC_SRC}/tinymt32.cc
        ${GIAC_SRC}/sha256.c
        ${GIAC_SRC}/first.cc)

add_library(giac_core STATIC ${GIAC_CORE_SOURCES})
target_include_directories(giac_core PRIVATE ${GIAC_SRC} ${GIAC_SOURCE_DIR} ${LIBTOMMATH_DIR})
target_compile_definitions(giac_core PRIVATE
        HAVE_CONFIG_H
        IN_GIAC
        __ANDROID__
        GIAC_GENERIC_CONSTANTS
        C11_UNORDERED_MAP)
target_compile_options(giac_core PRIVATE
        $<$<CONFIG:Debug>:-O2>
        $<$<CONFIG:Release>:-O3>
        -fPIC
        -fno-strict-aliasing
        -Wno-deprecated
        -Wno-deprecated-declarations
        -Wno-unused-parameter
        -Wno-unused-variable
        -Wno-unused-result
        -Wno-parentheses
        -Wno-switch)

add_library(calcora SHARED native-lib.cpp)

target_compile_options(calcora PRIVATE $<$<CONFIG:Debug>:-O2> $<$<CONFIG:Release>:-O3>)
target_compile_definitions(calcora PRIVATE
        CALCULATORPLUS_GIAC_SOURCE="${GIAC_SOURCE_DIR}"
        CALCULATORPLUS_WITH_GIAC=1
        HAVE_CONFIG_H
        IN_GIAC
        __ANDROID__
        GIAC_GENERIC_CONSTANTS
        C11_UNORDERED_MAP)
target_include_directories(calcora PRIVATE ${GIAC_SRC} ${GIAC_SOURCE_DIR} ${LIBTOMMATH_DIR})

target_link_libraries(giac_core PUBLIC libtommath)
target_link_libraries(calcora giac_core libtommath android log z)
