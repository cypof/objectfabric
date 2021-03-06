//==============================================================================
//                                                                              
//  THIS FILE HAS BEEN GENERATED BY OBJECTFABRIC                                
//                                                                              
//==============================================================================

namespace org.objectfabric {

public class SerializationTestWriter : org.objectfabric.SerializationTest.TestWriter {

    private int _index;

    public override void run() {
        for (;;) {
            switch (_index) {
                case 0: {
                    if (!canWriteBoolean())
                        return;

                    writeBoolean(false);
                    break;
                }
                case 1: {
                    if (!canWriteBoolean())
                        return;

                    writeBoolean(true);
                    break;
                }
                case 2: {
                    if (!canWriteBooleanBoxed())
                        return;

                    writeBooleanBoxed(null);
                    break;
                }
                case 3: {
                    if (!canWriteBooleanBoxed())
                        return;

                    writeBooleanBoxed(true);
                    break;
                }
                case 4: {
                    if (!canWriteBooleanBoxed())
                        return;

                    writeBooleanBoxed(false);
                    break;
                }
                case 5: {
                    if (!canWriteByte())
                        return;

                    writeByte(((byte) 0));
                    break;
                }
                case 6: {
                    if (!canWriteByte())
                        return;

                    writeByte(byte.MaxValue);
                    break;
                }
                case 7: {
                    if (!canWriteByte())
                        return;

                    writeByte(byte.MaxValue);
                    break;
                }
                case 8: {
                    if (!canWriteByteBoxed())
                        return;

                    writeByteBoxed(null);
                    break;
                }
                case 9: {
                    if (!canWriteCharacter())
                        return;

                    writeCharacter('\0');
                    break;
                }
                case 10: {
                    if (!canWriteCharacter())
                        return;

                    writeCharacter(char.MaxValue);
                    break;
                }
                case 11: {
                    if (!canWriteCharacter())
                        return;

                    writeCharacter(char.MaxValue);
                    break;
                }
                case 12: {
                    if (!canWriteCharacterBoxed())
                        return;

                    writeCharacterBoxed(null);
                    break;
                }
                case 13: {
                    if (!canWriteShort())
                        return;

                    writeShort(((short) 0));
                    break;
                }
                case 14: {
                    if (!canWriteShort())
                        return;

                    writeShort(short.MaxValue);
                    break;
                }
                case 15: {
                    if (!canWriteShort())
                        return;

                    writeShort(short.MaxValue);
                    break;
                }
                case 16: {
                    if (!canWriteShortBoxed())
                        return;

                    writeShortBoxed(null);
                    break;
                }
                case 17: {
                    if (!canWriteInteger())
                        return;

                    writeInteger(0);
                    break;
                }
                case 18: {
                    if (!canWriteInteger())
                        return;

                    writeInteger(int.MaxValue);
                    break;
                }
                case 19: {
                    if (!canWriteInteger())
                        return;

                    writeInteger(int.MaxValue);
                    break;
                }
                case 20: {
                    if (!canWriteIntegerBoxed())
                        return;

                    writeIntegerBoxed(null);
                    break;
                }
                case 21: {
                    if (!canWriteLong())
                        return;

                    writeLong(0);
                    break;
                }
                case 22: {
                    if (!canWriteLong())
                        return;

                    writeLong(long.MaxValue);
                    break;
                }
                case 23: {
                    if (!canWriteLong())
                        return;

                    writeLong(long.MaxValue);
                    break;
                }
                case 24: {
                    if (!canWriteLongBoxed())
                        return;

                    writeLongBoxed(null);
                    break;
                }
                case 25: {
                    if (!canWriteFloat())
                        return;

                    writeFloat(0);
                    break;
                }
                case 26: {
                    if (!canWriteFloat())
                        return;

                    writeFloat(float.MaxValue);
                    break;
                }
                case 27: {
                    if (!canWriteFloat())
                        return;

                    writeFloat(float.MaxValue);
                    break;
                }
                case 28: {
                    if (!canWriteFloatBoxed())
                        return;

                    writeFloatBoxed(null);
                    break;
                }
                case 29: {
                    if (!canWriteDouble())
                        return;

                    writeDouble(0);
                    break;
                }
                case 30: {
                    if (!canWriteDouble())
                        return;

                    writeDouble(double.MaxValue);
                    break;
                }
                case 31: {
                    if (!canWriteDouble())
                        return;

                    writeDouble(double.MaxValue);
                    break;
                }
                case 32: {
                    if (!canWriteDoubleBoxed())
                        return;

                    writeDoubleBoxed(null);
                    break;
                }
                case 33: {
                    writeString(null);

                    if (interrupted())
                        return;

                    break;
                }
                case 34: {
                    writeString("");

                    if (interrupted())
                        return;

                    break;
                }
                case 35: {
                    writeString("\u0000");

                    if (interrupted())
                        return;

                    break;
                }
                case 36: {
                    writeString("\u00FF");

                    if (interrupted())
                        return;

                    break;
                }
                case 37: {
                    writeString("\u0AFF");

                    if (interrupted())
                        return;

                    break;
                }
                case 38: {
                    writeString("\u7FFF");

                    if (interrupted())
                        return;

                    break;
                }
                case 39: {
                    writeString("\uFFFF");

                    if (interrupted())
                        return;

                    break;
                }
                case 40: {
                    writeString("ffqsdfqfezghrtghrgrfgzefzeqfzeqfqzefqzefqzefqzeefqzefqzefsdqfsdghfgzegqzefqsdfqzefqezfqzefqze'");

                    if (interrupted())
                        return;

                    break;
                }
                case 41: {
                    if (!canWriteDate())
                        return;

                    writeDate(null);
                    break;
                }
                case 42: {
                    if (!canWriteDate())
                        return;

                    writeDate(new System.DateTime( 4558621531843L * 10000L + 621355968000000000L, System.DateTimeKind.Utc ));
                    break;
                }
                case 43: {
                    if (!canWriteDate())
                        return;

                    writeDate(System.DateTime.Parse( "1/1/1970 00:00:00", null, System.Globalization.DateTimeStyles.AssumeUniversal ));
                    break;
                }
                case 44: {
                    writeBigInteger(null);

                    if (interrupted())
                        return;

                    break;
                }
                case 45: {
                    writeBigInteger(System.Numerics.BigInteger.Parse("0"));

                    if (interrupted())
                        return;

                    break;
                }
                case 46: {
                    writeBigInteger(System.Numerics.BigInteger.Parse("-0"));

                    if (interrupted())
                        return;

                    break;
                }
                case 47: {
                    writeBigInteger(System.Numerics.BigInteger.Parse("45"));

                    if (interrupted())
                        return;

                    break;
                }
                case 48: {
                    writeBigInteger(System.Numerics.BigInteger.Parse("-45"));

                    if (interrupted())
                        return;

                    break;
                }
                case 49: {
                    writeBigInteger(System.Numerics.BigInteger.Parse("1237987"));

                    if (interrupted())
                        return;

                    break;
                }
                case 50: {
                    writeBigInteger(System.Numerics.BigInteger.Parse("-1237987"));

                    if (interrupted())
                        return;

                    break;
                }
                case 51: {
                    writeBigInteger(System.Numerics.BigInteger.Parse("1237987898798797464864181688684513518313131813113513"));

                    if (interrupted())
                        return;

                    break;
                }
                case 52: {
                    writeBigInteger(System.Numerics.BigInteger.Parse("-1237987898798797464864181688684513518313131813113513"));

                    if (interrupted())
                        return;

                    break;
                }
                case 53: {
                    writeDecimal(null);

                    if (interrupted())
                        return;

                    break;
                }
                case 54: {
                    writeDecimal(0m);

                    if (interrupted())
                        return;

                    break;
                }
                case 55: {
                    writeDecimal(-0m);

                    if (interrupted())
                        return;

                    break;
                }
                case 56: {
                    writeDecimal(45m);

                    if (interrupted())
                        return;

                    break;
                }
                case 57: {
                    writeDecimal(-45m);

                    if (interrupted())
                        return;

                    break;
                }
                case 58: {
                    writeDecimal(123798789879879.456464m);

                    if (interrupted())
                        return;

                    break;
                }
                case 59: {
                    writeDecimal(-123798789879879.456464m);

                    if (interrupted())
                        return;

                    break;
                }
                case 60: {
                    writeDecimal(0.000000000000078m);

                    if (interrupted())
                        return;

                    break;
                }
                case 61: {
                    writeDecimal(-0.000000000000078m);

                    if (interrupted())
                        return;

                    break;
                }
                case 62: {
                    writeDecimal(2.0e5m);

                    if (interrupted())
                        return;

                    break;
                }
                case 63: {
                    writeDecimal(-2.0e5m);

                    if (interrupted())
                        return;

                    break;
                }
                case 64: {
                    writeDecimal(789.046544654846468789486e13m);

                    if (interrupted())
                        return;

                    break;
                }
                case 65: {
                    writeDecimal(-789.046544654846468789486e13m);

                    if (interrupted())
                        return;

                    break;
                }
                case 66: {
                    writeDecimal(789.046544654846468789486e-13m);

                    if (interrupted())
                        return;

                    break;
                }
                case 67: {
                    writeDecimal(-789.046544654846468789486e-13m);

                    if (interrupted())
                        return;

                    break;
                }
                case 68: {
                    writeBinary(null);

                    if (interrupted())
                        return;

                    break;
                }
                case 69: {
                    writeBinary(new byte[] { 45, 88 });

                    if (interrupted())
                        return;

                    break;
                }
                default:
                    return;
            }

            _index++;
        }
    }

}
}
