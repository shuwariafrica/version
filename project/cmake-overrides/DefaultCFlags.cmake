# Override shim resolved via CMAKE_MODULE_PATH ahead of libgit2's own
# cmake/ directory. Delegates to libgit2's DefaultCFlags.cmake (by absolute
# path so the module-name lookup does not recurse), then strips /GL from
# every release-style C and CXX flag set so MSVC produces standard COFF
# objects rather than LTCG-format objects that lld-link rejects.
include("${PROJECT_SOURCE_DIR}/cmake/DefaultCFlags.cmake")

# CMake regex has no word-boundary; match /GL bordered by whitespace or
# end-of-string. Capture group preserves the trailing whitespace so flag
# spacing stays valid after substitution.
foreach(cfg IN ITEMS RELEASE RELWITHDEBINFO MINSIZEREL)
  foreach(lang IN ITEMS C CXX)
    set(var CMAKE_${lang}_FLAGS_${cfg})
    if(DEFINED ${var})
      string(REGEX REPLACE "[ \t]+/GL([ \t]|$)" "\\1" ${var} "${${var}}")
    endif()
  endforeach()
endforeach()
