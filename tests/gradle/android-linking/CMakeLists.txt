# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.

cmake_minimum_required(VERSION 3.10)

project(android-linking-cpp)

add_library(android-linking-cpp SHARED src/main/cpp/android-linking-cpp.cpp)
