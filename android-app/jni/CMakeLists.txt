cmake_minimum_required(VERSION 3.22)
project(nexa_llama)

# llama.cpp كمكتبة بس — من غير أدوات ولا أمثلة ولا سيرفر
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
# ندمج باك إند الـ CPU جوه libggml بدل ما يتحمل كملف منفصل وقت التشغيل
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp)

add_library(nexa_llama SHARED nexa_llama.cpp)
target_link_libraries(nexa_llama llama android log)
