///
/// This file is part of ObjectFabric (http://objectfabric.org).
///
/// ObjectFabric is licensed under the Apache License, Version 2.0, the terms
/// of which may be found at http://www.apache.org/licenses/LICENSE-2.0.html.
///
/// Copyright ObjectFabric Inc.
///
/// This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
/// WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
///

using System;
using System.Globalization;
using System.Numerics;

namespace ObjectFabric
{
    public struct ByteConverter
    {
        private readonly bool _hasValue;
        private readonly byte _value;

        public ByteConverter( bool hasValue, byte value )
        {
            _hasValue = hasValue;
            _value = value;
        }

        public bool HasValue
        {
            get { return _hasValue; }
        }

        public byte Value
        {
            get { return _value; }
        }

        public static implicit operator byte?( ByteConverter value )
        {
            return value.HasValue ? value.Value : (byte?) null;
        }

        public static implicit operator ByteConverter( byte? value )
        {
            return value.HasValue ? new ByteConverter( true, value.Value ) : new ByteConverter( false, 0 );
        }

        public static object Box( byte value )
        {
            return value;
        }

        public static byte Unbox( object value )
        {
            return (byte) value;
        }
    }

    public struct BooleanConverter
    {
        private readonly bool _hasValue;
        private readonly bool _value;

        public BooleanConverter( bool hasValue, bool value )
        {
            _hasValue = hasValue;
            _value = value;
        }

        public bool HasValue
        {
            get { return _hasValue; }
        }

        public bool Value
        {
            get { return _value; }
        }

        public static implicit operator bool?( BooleanConverter value )
        {
            return value.HasValue ? value.Value : (bool?) null;
        }

        public static implicit operator BooleanConverter( bool? value )
        {
            return value.HasValue ? new BooleanConverter( true, value.Value ) : new BooleanConverter( false, false );
        }

        public static object Box( bool value )
        {
            return value;
        }

        public static bool Unbox( object value )
        {
            return (bool) value;
        }
    }

    public struct ShortConverter
    {
        private readonly bool _hasValue;
        private readonly short _value;

        public ShortConverter( bool hasValue, short value )
        {
            _hasValue = hasValue;
            _value = value;
        }

        public bool HasValue
        {
            get { return _hasValue; }
        }

        public short Value
        {
            get { return _value; }
        }

        public static implicit operator short?( ShortConverter value )
        {
            return value.HasValue ? value.Value : (short?) null;
        }

        public static implicit operator ShortConverter( short? value )
        {
            return value.HasValue ? new ShortConverter( true, value.Value ) : new ShortConverter( false, 0 );
        }

        public static object Box( short value )
        {
            return value;
        }

        public static short Unbox( object value )
        {
            return (short) value;
        }
    }

    public struct CharacterConverter
    {
        private readonly bool _hasValue;
        private readonly char _value;

        public CharacterConverter( bool hasValue, char value )
        {
            _hasValue = hasValue;
            _value = value;
        }

        public bool HasValue
        {
            get { return _hasValue; }
        }

        public char Value
        {
            get { return _value; }
        }

        public static implicit operator char?( CharacterConverter value )
        {
            return value.HasValue ? value.Value : (char?) null;
        }

        public static implicit operator CharacterConverter( char? value )
        {
            return value.HasValue ? new CharacterConverter( true, value.Value ) : new CharacterConverter( false, (char) 0 );
        }

        public static object Box( char value )
        {
            return value;
        }

        public static char Unbox( object value )
        {
            return (char) value;
        }
    }

    public struct IntegerConverter
    {
        private readonly bool _hasValue;
        private readonly int _value;

        public IntegerConverter( bool hasValue, int value )
        {
            _hasValue = hasValue;
            _value = value;
        }

        public bool HasValue
        {
            get { return _hasValue; }
        }

        public int Value
        {
            get { return _value; }
        }

        public static implicit operator int?( IntegerConverter value )
        {
            return value.HasValue ? value.Value : (int?) null;
        }

        public static implicit operator IntegerConverter( int? value )
        {
            return value.HasValue ? new IntegerConverter( true, value.Value ) : new IntegerConverter( false, 0 );
        }

        public static object Box( int value )
        {
            return value;
        }

        public static int Unbox( object value )
        {
            return (int) value;
        }
    }

    public struct LongConverter
    {
        private readonly bool _hasValue;
        private readonly long _value;

        public LongConverter( bool hasValue, long value )
        {
            _hasValue = hasValue;
            _value = value;
        }

        public bool HasValue
        {
            get { return _hasValue; }
        }

        public long Value
        {
            get { return _value; }
        }

        public static implicit operator long?( LongConverter value )
        {
            return value.HasValue ? value.Value : (long?) null;
        }

        public static implicit operator LongConverter( long? value )
        {
            return value.HasValue ? new LongConverter( true, value.Value ) : new LongConverter( false, 0 );
        }

        public static object Box( long value )
        {
            return value;
        }

        public static long Unbox( object value )
        {
            return (long) value;
        }
    }

    public struct FloatConverter
    {
        private readonly bool _hasValue;
        private readonly float _value;

        public FloatConverter( bool hasValue, float value )
        {
            _hasValue = hasValue;
            _value = value;
        }

        public bool HasValue
        {
            get { return _hasValue; }
        }

        public float Value
        {
            get { return _value; }
        }

        public static implicit operator float?( FloatConverter value )
        {
            return value.HasValue ? value.Value : (float?) null;
        }

        public static implicit operator FloatConverter( float? value )
        {
            return value.HasValue ? new FloatConverter( true, value.Value ) : new FloatConverter( false, 0 );
        }

        public static object Box( float value )
        {
            return value;
        }

        public static float Unbox( object value )
        {
            return (float) value;
        }

        public static int ToInt( float value )
        {
            IKVM.Runtime.FloatConverter converter = new IKVM.Runtime.FloatConverter();
            return IKVM.Runtime.FloatConverter.ToInt( value, ref converter );
        }

        public static float ToFloat( int value )
        {
            IKVM.Runtime.FloatConverter converter = new IKVM.Runtime.FloatConverter();
            return IKVM.Runtime.FloatConverter.ToFloat( value, ref converter );
        }

        public static string ToString( float value )
        {
            return value.ToString( "G9", CultureInfo.InvariantCulture );
        }

        public static float ToFloat( string value )
        {
            return float.Parse( value, CultureInfo.InvariantCulture );
        }
    }

    public struct DoubleConverter
    {
        private readonly bool _hasValue;
        private readonly double _value;

        public DoubleConverter( bool hasValue, double value )
        {
            _hasValue = hasValue;
            _value = value;
        }

        public bool HasValue
        {
            get { return _hasValue; }
        }

        public double Value
        {
            get { return _value; }
        }

        public static implicit operator double?( DoubleConverter value )
        {
            return value.HasValue ? value.Value : (double?) null;
        }

        public static implicit operator DoubleConverter( double? value )
        {
            return value.HasValue ? new DoubleConverter( true, value.Value ) : new DoubleConverter( false, 0 );
        }

        public static object Box( double value )
        {
            return value;
        }

        public static double Unbox( object value )
        {
            return (double) value;
        }

        public static long ToLong( double value )
        {
            IKVM.Runtime.DoubleConverter converter = new IKVM.Runtime.DoubleConverter();
            return IKVM.Runtime.DoubleConverter.ToLong( value, ref converter );
        }

        public static double ToDouble( long value )
        {
            IKVM.Runtime.DoubleConverter converter = new IKVM.Runtime.DoubleConverter();
            return IKVM.Runtime.DoubleConverter.ToDouble( value, ref converter );
        }

        public static string ToString( double value )
        {
            return value.ToString( "G17", CultureInfo.InvariantCulture );
        }

        public static double ToDouble( string value )
        {
            return double.Parse( value, CultureInfo.InvariantCulture );
        }
    }

    public struct DateConverter
    {
        private readonly bool _hasValue;
        private readonly long _ticks;

        public DateConverter( DateTime value )
        {
            _hasValue = true;

            if( value.Kind == DateTimeKind.Local )
                value = value.ToUniversalTime();

            _ticks = value.Ticks;
        }

        public DateConverter( bool hasValue, long ticks )
        {
            _hasValue = hasValue;
            _ticks = ticks;
        }

        public bool HasValue
        {
            get { return _hasValue; }
        }

        public long Ticks
        {
            get { return _ticks; }
        }

        public static implicit operator DateTime?( DateConverter value )
        {
            return value.HasValue ? new DateTime( value.Ticks, DateTimeKind.Utc ) : (DateTime?) null;
        }

        public static implicit operator DateConverter( DateTime? value )
        {
            return value.HasValue ? new DateConverter( true, value.Value.Ticks ) : new DateConverter( false, 0 );
        }
    }

    public struct BigIntegerConverter
    {
        private readonly bool _hasValue;
        private readonly BigInteger _value;

        public BigIntegerConverter( bool hasValue, BigInteger value )
        {
            _hasValue = hasValue;
            _value = value;
        }

        public bool HasValue
        {
            get { return _hasValue; }
        }

        public static implicit operator BigInteger?( BigIntegerConverter value )
        {
            if( !value.HasValue )
                return null;

            return value._value;
        }

        public static implicit operator BigIntegerConverter( BigInteger? value )
        {
            if( !value.HasValue )
                return new BigIntegerConverter( false, 0 );

            return new BigIntegerConverter( true, value.Value );
        }

        public byte[] toBytes()
        {
            byte[] bytes = _value.ToByteArray();
            SwitchEndianess( bytes );
            return bytes;
        }

        public static BigIntegerConverter fromBytes( byte[] bytes )
        {
            SwitchEndianess( bytes );
            return new BigIntegerConverter( true, new BigInteger( bytes ) );
        }

        public static void SwitchEndianess( byte[] array )
        {
            for( int i = 0; i < array.Length / 2; i++ )
            {
                byte temp = array[i];
                array[i] = array[array.Length - i - 1];
                array[array.Length - i - 1] = temp;
            }
        }
    }

    public struct DecimalConverter
    {
        private readonly bool _hasValue;
        private readonly byte[] _unscaled;
        private readonly int _scale;

        public DecimalConverter( decimal value )
        {
            _hasValue = true;
            int[] ints = decimal.GetBits( value );
            byte[] bytes = new byte[12];
            writeInt( ints[0], bytes, 0 );
            writeInt( ints[1], bytes, 4 );
            writeInt( ints[2], bytes, 8 );
            BigInteger unscaled = new BigInteger( bytes );
            BigInteger signed = value < 0 ? -unscaled : unscaled;
            _unscaled = signed.ToByteArray();
            BigIntegerConverter.SwitchEndianess( _unscaled );
            _scale = (ints[3] >> 16) & 0xFF;
        }

        public DecimalConverter( bool hasValue, byte[] unscaled, int scale )
        {
            _hasValue = hasValue;
            _unscaled = unscaled;
            _scale = scale;
        }

        public bool HasValue
        {
            get { return _hasValue; }
        }

        public byte[] Unscaled
        {
            get { return _unscaled; }
        }

        public int Scale
        {
            get { return _scale; }
        }

        public static implicit operator decimal?( DecimalConverter value )
        {
            if( !value.HasValue )
                return null;

            BigIntegerConverter.SwitchEndianess( value._unscaled );
            BigInteger signed = new BigInteger( value._unscaled );
            BigInteger unscaled = signed < 0 ? -signed : signed;
            int scale = value._scale;

            if( scale < 0 )
            {
                unscaled = unscaled * BigInteger.Pow( BigInteger.One, -scale + 1 );
                scale = 0;
            }

            byte[] bytes = unscaled.ToByteArray();

            if( bytes.Length > 12 || scale > 28 )
                throw new OverflowException( "Java BigDecimal was too large for .NET decimal" );

            byte[] twelve = new byte[12];
            Array.Copy( bytes, 0, twelve, twelve.Length - bytes.Length, bytes.Length );
            return new decimal( readInt( twelve, 0 ), readInt( twelve, 4 ), readInt( twelve, 8 ), signed.Sign < 0, (byte) scale );
        }

        public static implicit operator DecimalConverter( decimal? value )
        {
            if( !value.HasValue )
                return new DecimalConverter( false, null, 0 );

            return new DecimalConverter( value.Value );
        }

        private static int readInt( byte[] array, int index )
        {
            int b0 = array[index + 0] << 24;
            int b1 = array[index + 1] << 16;
            int b2 = array[index + 2] << 8;
            int b3 = array[index + 3] << 0;
            return b3 | b2 | b1 | b0;
        }

        private static void writeInt( int value, byte[] array, int index )
        {
            array[index + 0] = ((byte) ((value >> 0) & 0xFF));
            array[index + 1] = ((byte) ((value >> 8) & 0xFF));
            array[index + 2] = ((byte) ((value >> 16) & 0xFF));
            array[index + 3] = ((byte) ((value >> 24) & 0xFF));
        }
    }
}
