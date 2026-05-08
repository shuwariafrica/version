# Override shim resolved via CMAKE_MODULE_PATH ahead of libgit2's own
# cmake/ directory. Delegates to libgit2's DefaultCFlags.cmake (by absolute
# path so the module-name lookup does not recurse), then strips /GL from
# every release-style C flag set.
#
# Why: libgit2 unconditionally adds /GL to release C flags when MSVC is
# detected. Under VS-bundled clang-cl that turns object files into LLVM
# bitcode which scala-native's lld-link rejects unless LTO mode is active —
# and our test binaries do not link with LTO.
include("${PROJECT_SOURCE_DIR}/cmake/DefaultCFlags.cmake")

foreach(cfg IN ITEMS RELEASE RELWITHDEBINFO MINSIZEREL)
  if(DEFINED CMAKE_C_FLAGS_${cfg})
    string(REGEX REPLACE "[ \t]+/GL\\b" "" CMAKE_C_FLAGS_${cfg} "${CMAKE_C_FLAGS_${cfg}}")
  endif()
endforeach()
