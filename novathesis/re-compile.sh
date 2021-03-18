#!/usr/bin/bash

pdflatex template.tex ; biber template ; pdflatex template.tex
