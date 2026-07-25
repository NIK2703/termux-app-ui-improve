// Auto-generated from VTE box-drawing.hh (109 glyphs)

package com.termux.view.graphics;

import android.util.SparseArray;

final class BoxDrawing {
    static final SparseArray<int[]> TABLE = new SparseArray<>(109);

    static {
        TABLE.put(0x2500, new int[]{0,0,31,0,0}); // light horizontal
        TABLE.put(0x2501, new int[]{0,31,31,31,0}); // heavy horizontal
        TABLE.put(0x2502, new int[]{4,4,4,4,4}); // light vertical
        TABLE.put(0x2503, new int[]{14,14,14,14,14}); // heavy vertical
        TABLE.put(0x250C, new int[]{0,0,7,4,4}); // light down and right
        TABLE.put(0x250D, new int[]{0,7,7,7,4}); // down light and right heavy
        TABLE.put(0x250E, new int[]{0,0,15,14,14}); // down heavy and right light
        TABLE.put(0x250F, new int[]{0,15,15,15,14}); // heavy down and right
        TABLE.put(0x2510, new int[]{0,0,28,4,4}); // light down and left
        TABLE.put(0x2511, new int[]{0,28,28,28,4}); // down light and left heavy
        TABLE.put(0x2512, new int[]{0,0,30,14,14}); // down heavy and left light
        TABLE.put(0x2513, new int[]{0,30,30,30,14}); // heavy down and left
        TABLE.put(0x2514, new int[]{4,4,7,0,0}); // light up and right
        TABLE.put(0x2515, new int[]{4,7,7,7,0}); // up light and right heavy
        TABLE.put(0x2516, new int[]{14,14,15,0,0}); // up heavy and right light
        TABLE.put(0x2517, new int[]{14,15,15,15,0}); // heavy up and right
        TABLE.put(0x2518, new int[]{4,4,28,0,0}); // light up and left
        TABLE.put(0x2519, new int[]{4,28,28,28,0}); // up light and left heavy
        TABLE.put(0x251A, new int[]{14,14,30,0,0}); // up heavy and left light
        TABLE.put(0x251B, new int[]{14,30,30,30,0}); // heavy up and left
        TABLE.put(0x251C, new int[]{4,4,7,4,4}); // light vertical and right
        TABLE.put(0x251D, new int[]{4,7,7,7,4}); // vertical light and right heavy
        TABLE.put(0x251E, new int[]{14,14,15,4,4}); // up heavy and right down light
        TABLE.put(0x251F, new int[]{4,4,15,14,14}); // down heavy and right up light
        TABLE.put(0x2520, new int[]{14,14,15,14,14}); // vertical heavy and right light
        TABLE.put(0x2521, new int[]{14,15,15,15,4}); // down light and right up heavy
        TABLE.put(0x2522, new int[]{4,15,15,15,14}); // up light and right down heavy
        TABLE.put(0x2523, new int[]{14,15,15,15,14}); // heavy vertical and right
        TABLE.put(0x2524, new int[]{4,4,28,4,4}); // light vertical and left
        TABLE.put(0x2525, new int[]{4,28,28,28,4}); // vertical light and left heavy
        TABLE.put(0x2526, new int[]{14,14,30,4,4}); // up heavy and left down light
        TABLE.put(0x2527, new int[]{4,4,30,14,14}); // down heavy and left up light
        TABLE.put(0x2528, new int[]{14,14,30,14,14}); // vertical heavy and left light
        TABLE.put(0x2529, new int[]{14,30,30,30,4}); // down light and left up heavy
        TABLE.put(0x252A, new int[]{4,30,30,30,14}); // up light and left down heavy
        TABLE.put(0x252B, new int[]{14,30,30,30,14}); // heavy vertical and left
        TABLE.put(0x252C, new int[]{0,0,31,4,4}); // light down and horizontal
        TABLE.put(0x252D, new int[]{0,28,31,28,4}); // left heavy and right down light
        TABLE.put(0x252E, new int[]{0,7,31,7,4}); // right heavy and left down light
        TABLE.put(0x252F, new int[]{0,31,31,31,4}); // down light and horizontal heavy
        TABLE.put(0x2530, new int[]{0,0,31,14,14}); // down heavy and horizontal light
        TABLE.put(0x2531, new int[]{0,30,31,30,14}); // right light and left down heavy
        TABLE.put(0x2532, new int[]{0,15,31,15,14}); // left light and right down heavy
        TABLE.put(0x2533, new int[]{0,31,31,31,14}); // heavy down and horizontal
        TABLE.put(0x2534, new int[]{4,4,31,0,0}); // light up and horizontal
        TABLE.put(0x2535, new int[]{4,28,31,28,0}); // left heavy and right up light
        TABLE.put(0x2536, new int[]{4,7,31,7,0}); // right heavy and left up light
        TABLE.put(0x2537, new int[]{4,31,31,31,0}); // up light and horizontal heavy
        TABLE.put(0x2538, new int[]{14,14,31,0,0}); // up heavy and horizontal light
        TABLE.put(0x2539, new int[]{14,30,31,30,0}); // right light and left up heavy
        TABLE.put(0x253A, new int[]{14,15,31,15,0}); // left light and right up heavy
        TABLE.put(0x253B, new int[]{14,31,31,31,0}); // heavy up and horizontal
        TABLE.put(0x253C, new int[]{4,4,31,4,4}); // light vertical and horizontal
        TABLE.put(0x253D, new int[]{4,28,31,28,4}); // left heavy and right vertical light
        TABLE.put(0x253E, new int[]{4,7,31,7,4}); // right heavy and left vertical light
        TABLE.put(0x253F, new int[]{4,31,31,31,4}); // vertical light and horizontal heavy
        TABLE.put(0x2540, new int[]{14,14,31,4,4}); // up heavy and down horizontal light
        TABLE.put(0x2541, new int[]{4,4,31,14,14}); // down heavy and up horizontal light
        TABLE.put(0x2542, new int[]{14,14,31,14,14}); // vertical heavy and horizontal light
        TABLE.put(0x2543, new int[]{14,30,31,30,4}); // left up heavy and right down light
        TABLE.put(0x2544, new int[]{14,15,31,15,4}); // right up heavy and left down light
        TABLE.put(0x2545, new int[]{4,30,31,30,14}); // left down heavy and right up light
        TABLE.put(0x2546, new int[]{4,15,31,15,14}); // right down heavy and left up light
        TABLE.put(0x2547, new int[]{14,31,31,31,4}); // down light and up horizontal heavy
        TABLE.put(0x2548, new int[]{4,31,31,31,14}); // up light and down horizontal heavy
        TABLE.put(0x2549, new int[]{14,30,31,30,14}); // right light and left vertical heavy
        TABLE.put(0x254A, new int[]{14,15,31,15,14}); // left light and right vertical heavy
        TABLE.put(0x254B, new int[]{14,31,31,31,14}); // heavy vertical and horizontal
        TABLE.put(0x2550, new int[]{0,31,0,31,0}); // double horizontal
        TABLE.put(0x2551, new int[]{10,10,10,10,10}); // double vertical
        TABLE.put(0x2552, new int[]{0,7,4,7,4}); // down single and right double
        TABLE.put(0x2553, new int[]{0,0,15,10,10}); // down double and right single
        TABLE.put(0x2554, new int[]{0,15,8,11,10}); // double down and right
        TABLE.put(0x2555, new int[]{0,28,4,28,4}); // down single and left double
        TABLE.put(0x2556, new int[]{0,0,30,10,10}); // down double and left single
        TABLE.put(0x2557, new int[]{0,30,2,26,10}); // double down and left
        TABLE.put(0x2558, new int[]{4,7,4,7,0}); // up single and right double
        TABLE.put(0x2559, new int[]{10,10,15,0,0}); // up double and right single
        TABLE.put(0x255A, new int[]{10,11,8,15,0}); // double up and right
        TABLE.put(0x255B, new int[]{4,28,4,28,0}); // up single and left double
        TABLE.put(0x255C, new int[]{10,10,30,0,0}); // up double and left single
        TABLE.put(0x255D, new int[]{10,26,2,30,0}); // double up and left
        TABLE.put(0x255E, new int[]{4,7,4,7,4}); // vertical single and right double
        TABLE.put(0x255F, new int[]{10,10,11,10,10}); // vertical double and right single
        TABLE.put(0x2560, new int[]{10,11,8,11,10}); // double vertical and right
        TABLE.put(0x2561, new int[]{4,28,4,28,4}); // vertical single and left double
        TABLE.put(0x2562, new int[]{10,10,26,10,10}); // vertical double and left single
        TABLE.put(0x2563, new int[]{10,26,2,26,10}); // double vertical and left
        TABLE.put(0x2564, new int[]{0,31,0,31,4}); // down single and horizontal double
        TABLE.put(0x2565, new int[]{0,0,31,10,10}); // down double and horizontal single
        TABLE.put(0x2566, new int[]{0,31,0,27,10}); // double down and horizontal
        TABLE.put(0x2567, new int[]{4,31,0,31,0}); // up single and horizontal double
        TABLE.put(0x2568, new int[]{10,10,31,0,0}); // up double and horizontal single
        TABLE.put(0x2569, new int[]{10,27,0,31,0}); // double up and horizontal
        TABLE.put(0x256A, new int[]{4,31,4,31,4}); // vertical single and horizontal double
        TABLE.put(0x256B, new int[]{10,10,31,10,10}); // vertical double and horizontal single
        TABLE.put(0x256C, new int[]{10,27,0,27,10}); // double vertical and horizontal
        TABLE.put(0x2574, new int[]{0,0,28,0,0}); // light left
        TABLE.put(0x2575, new int[]{4,4,4,0,0}); // light up
        TABLE.put(0x2576, new int[]{0,0,7,0,0}); // light right
        TABLE.put(0x2577, new int[]{0,0,4,4,4}); // light down
        TABLE.put(0x2578, new int[]{0,28,28,28,0}); // heavy left
        TABLE.put(0x2579, new int[]{14,14,14,0,0}); // heavy up
        TABLE.put(0x257A, new int[]{0,7,7,7,0}); // heavy right
        TABLE.put(0x257B, new int[]{0,0,14,14,14}); // heavy down
        TABLE.put(0x257C, new int[]{0,7,31,7,0}); // light left and heavy right
        TABLE.put(0x257D, new int[]{4,4,14,14,14}); // light up and heavy down
        TABLE.put(0x257E, new int[]{0,28,31,28,0}); // heavy left and light right
        TABLE.put(0x257F, new int[]{14,14,14,4,4}); // heavy up and light down
    }
}
