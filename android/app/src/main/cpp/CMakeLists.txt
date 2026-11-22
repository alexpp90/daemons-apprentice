cmake_minimum_required(VERSION 3.22.1)
project(apprentice_pty C)
add_library(apprentice-pty SHARED pty.c)
target_compile_options(apprentice-pty PRIVATE -Wall -Wextra -O2)
