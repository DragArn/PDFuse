"""Bench CLI : python bench.py <pikepdf|pypdf|pymupdf> <out.pdf> <in...>"""
import sys

from merge_python import merge_pikepdf, merge_pypdf, merge_pymupdf

LIBS = {"pikepdf": merge_pikepdf, "pypdf": merge_pypdf, "pymupdf": merge_pymupdf}

if __name__ == "__main__":
    lib, dst, *inputs = sys.argv[1:]
    LIBS[lib](dst, inputs)
