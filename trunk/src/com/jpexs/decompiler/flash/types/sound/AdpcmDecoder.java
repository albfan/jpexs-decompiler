/*
 * Copyright (C) 2013 JPEXS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jpexs.decompiler.flash.types.sound;

import com.jpexs.decompiler.flash.EndOfStreamException;
import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.SWFInputStream;
import com.jpexs.decompiler.flash.SWFOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 *
 * @author JPEXS
 */
public class AdpcmDecoder {

    private static final int indexAdjustTable2bit[] = {
        -1, 2,
        -1, 2};
    private static final int indexAdjustTable3bit[] = {
        -1, -1, 2, 4,
        -1, -1, 2, 4};
    private static final int indexAdjustTable4bit[] = {
        -1, -1, -1, -1, 2, 4, 6, 8,
        -1, -1, -1, -1, 2, 4, 6, 8};
    private static final int indexAdjustTable5bit[] = {
        -1, -1, -1, -1, -1, -1, -1, -1, 1, 2, 4, 6, 8, 10, 13, 16,
        -1, -1, -1, -1, -1, -1, -1, -1, 1, 2, 4, 6, 8, 10, 13, 16};
    private static final int stepSizeTable[] = {
        7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34,
        37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143,
        157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494,
        544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552,
        1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026,
        4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442,
        11487, 12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623,
        27086, 29794, 32767
    };

    private static class AdpcmState {

        public int index;
        public int sample;
    };

    private static int decode2bit(int deltaCode, AdpcmState state) {
        assert (deltaCode == (deltaCode & 3));

        int step = stepSizeTable[state.index];

        int difference = step >> 1;
        if ((deltaCode & 1) == 1) {
            difference += step;
        }
        if ((deltaCode & 2) == 2) {
            difference = -difference;
        }

        state.sample += difference;
        if (state.sample > 32767) {
            state.sample = 32767;
        } else if (state.sample < -32768) {
            state.sample = -32768;
        }

        state.index += indexAdjustTable2bit[deltaCode];
        if (state.index < 0) {
            state.index = 0;
        } else if (state.index > 88) {
            state.index = 88;
        }

        return state.sample;
    }

    private static int decode3bit(int deltaCode, AdpcmState state) {
        assert (deltaCode == (deltaCode & 7));

        int step = stepSizeTable[state.index];

        int difference = step >> 2;
        if ((deltaCode & 1) == 1) {
            difference += step >> 1;
        }
        if ((deltaCode & 2) == 2) {
            difference += step;
        }
        if ((deltaCode & 4) == 4) {
            difference = -difference;
        }

        state.sample += difference;
        if (state.sample > 32767) {
            state.sample = 32767;
        } else if (state.sample < -32768) {
            state.sample = -32768;
        }

        state.index += indexAdjustTable3bit[deltaCode];
        if (state.index < 0) {
            state.index = 0;
        } else if (state.index > 88) {
            state.index = 88;
        }

        return state.sample;
    }

    private static int decode4bit(int deltaCode, AdpcmState state) {
        assert (deltaCode == (deltaCode & 15));

        int step = stepSizeTable[state.index];

        int difference = step >> 3;
        if ((deltaCode & 1) == 1) {
            difference += step >> 2;
        }
        if ((deltaCode & 2) == 2) {
            difference += step >> 1;
        }
        if ((deltaCode & 4) == 4) {
            difference += step;
        }
        if ((deltaCode & 8) == 8) {
            difference = -difference;
        }

        state.sample += difference;
        if (state.sample > 32767) {
            state.sample = 32767;
        } else if (state.sample < -32768) {
            state.sample = -32768;
        }

        state.index += indexAdjustTable4bit[deltaCode];
        if (state.index < 0) {
            state.index = 0;
        } else if (state.index > 88) {
            state.index = 88;
        }

        return state.sample;
    }

    private static int decode5bit(int deltaCode, AdpcmState state) {
        assert (deltaCode >= 0);
        assert (deltaCode <= 31 /* 2#11111 */);

        int step = stepSizeTable[state.index];

        int difference = step >> 4;
        if ((deltaCode & 1) == 1) {
            difference += step >> 3;
        }
        if ((deltaCode & 2) == 2) {
            difference += step >> 2;
        }
        if ((deltaCode & 4) == 4) {
            difference += step >> 1;
        }
        if ((deltaCode & 8) == 8) {
            difference += step;
        }
        if ((deltaCode & 16) == 16) {
            difference = -difference;
        }

        state.sample += difference;
        if (state.sample > 32767) {
            state.sample = 32767;
        } else if (state.sample < -32768) {
            state.sample = -32768;
        }

        state.index += indexAdjustTable5bit[deltaCode];
        if (state.index < 0) {
            state.index = 0;
        } else if (state.index > 88) {
            state.index = 88;
        }

        return state.sample;
    }

    public static byte[] decode(byte data[], boolean isStereo) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        decode(new ByteArrayInputStream(data), baos, isStereo);
        return baos.toByteArray();
    }

    public static void decode(InputStream is, OutputStream os, boolean is_stereo) throws IOException {
        int adpcm_code_size;
        SWFInputStream sis = new SWFInputStream(is, SWF.DEFAULT_VERSION);
        SWFOutputStream sos = new SWFOutputStream(os, SWF.DEFAULT_VERSION);
        adpcm_code_size = (int) sis.readUB(2);
        int bits_per_code = adpcm_code_size + 2;
        try {
            do {
                if (is_stereo) {
                    int initialSampleLeft = (int) sis.readSB(16);
                    int initialIndexLeft = (int) sis.readUB(6);
                    int initialSampleRight = (int) sis.readSB(16);
                    int initialIndexRight = (int) sis.readUB(6);
                    AdpcmState stateLeft = new AdpcmState();
                    stateLeft.index = initialIndexLeft;
                    stateLeft.sample = initialSampleLeft;
                    AdpcmState stateRight = new AdpcmState();
                    stateRight.index = initialIndexRight;
                    stateRight.sample = initialSampleRight;
                    for (int i = 1; (i <= 4095) && (sis.availableBits() >= bits_per_code * 2); i++) {
                        int codeLeft = (int) sis.readUB(bits_per_code);
                        int codeRight = (int) sis.readUB(bits_per_code);
                        int valLeft = 0;
                        int valRight = 0;
                        switch (bits_per_code) {
                            case 2:
                                valLeft = decode2bit(codeLeft, stateLeft);
                                valRight = decode2bit(codeRight, stateRight);
                                break;
                            case 3:
                                valLeft = decode3bit(codeLeft, stateLeft);
                                valRight = decode3bit(codeRight, stateRight);
                                break;
                            case 4:
                                valLeft = decode4bit(codeLeft, stateLeft);
                                valRight = decode4bit(codeRight, stateRight);
                                break;
                            case 5:
                                valLeft = decode5bit(codeLeft, stateLeft);
                                valRight = decode5bit(codeRight, stateRight);
                                break;
                        }
                        sos.writeSI16(valLeft);
                        sos.writeSI16(valRight);
                    }
                } else {
                    int initialSample = (int) sis.readSB(16);
                    int initialIndex = (int) sis.readUB(6);
                    AdpcmState state = new AdpcmState();
                    state.index = initialIndex;
                    state.sample = initialSample;
                    for (int i = 1; (i <= 4095) && (sis.availableBits() >= bits_per_code); i++) {
                        int code = (int) sis.readUB(bits_per_code);
                        int val = 0;
                        switch (bits_per_code) {
                            case 2:
                                val = decode2bit(code, state);
                                break;
                            case 3:
                                val = decode3bit(code, state);
                                break;
                            case 4:
                                val = decode4bit(code, state);
                                break;
                            case 5:
                                val = decode5bit(code, state);
                                break;
                        }
                        sos.writeSI16(val);
                    }
                }
            } while (sis.available() > 0);
        } catch (EndOfStreamException eos) {
        }
    }
}