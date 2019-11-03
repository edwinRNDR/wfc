# wfc

A wave-function collapse (WFC) library in Kotlin.

Implements 2D and 3D overlapping and tiling WFC models.

_wfc_ consists of three modules:

 - `wfc-core` - implementation of the WFC algorithms, fully free of dependencies
 - `wfc-openrndr` - collection of wrappers and extension methods to simplify use with OPENRNDR
 - `wfc-demo-openrndr` - collection of OPENRNDR based demos

## Quick start

Clone project, open in IntelliJ, run `WFCDemo001` in `wfc-demo-openrndr`.

[Expected outcome of WFCDemo001](https://twitter.com/voorbeeld/status/1071892898659078146)

In `wfc-demo-openrndr` you will also find AVDemo002 which demonstrates a 3D version of the WFC algorithm.

[![AVDemo002 Demo](https://img.youtube.com/vi/g4Ih8wxBh1E/0.jpg)](https://www.youtube.com/watch?v=g4Ih8wxBh1E)

## Attributions

This mostly follows the C# implementation in https://github.com/mxgmn/WaveFunctionCollapse.
