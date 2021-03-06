package io.github.jmcleodfoss.pst;

/**	The ByteUtil class contains utility functions for dealing with bytes. */
public class ByteUtil {

	/**	The number of 8-bit bytes in a long. */
	private static final int LONG_BYTES = Long.SIZE/Byte.SIZE;

	/**	This array of hex digits is used for conversion of values 0-15 to 0-9, A-F. */
	private final static char[] HEX_DIGIT = {
		'0',
		'1',
		'2',
		'3',
		'4',
		'5',
		'6',
		'7',
		'8',
		'9',
		'A',
		'B',
		'C',
		'D',
		'E',
		'F'
	};

	/**	Create a String representing a single byte in hexadecimal.
	*
	*	@param	b	The byte to convert to a hexadecimal string.
	*
	*	@return	The hexadecimal String corresponding to the given byte.
	*/
	public static String toHexString(final byte b)
	{
		StringBuilder s = new StringBuilder (2);
		s.append(HEX_DIGIT[(b & 0xff)/16]);
		s.append(HEX_DIGIT[(b & 0xff)%16]);
		return s.toString();
	}

	/**	Create a string representation expressing the given sequence of bytes in hexadecimal.
	*
	*	@param	bytes	The bytes to convert to a String of hexadecimal values.
	*
	*	@return	The String containing the hexadecimal representation of the given bytes.
	*/
	public static String createHexByteString(final byte[] bytes)
	{
		StringBuilder s = new StringBuilder (3*bytes.length);
		for (int i = 0; i < bytes.length; ++i) {
			if (i > 0)
				s.append(' ');
			s.append(HEX_DIGIT[(bytes[i] & 0xff)/16]);
			s.append(HEX_DIGIT[(bytes[i] & 0xff)%16]);
		}
		return s.toString();
	}


	/**	Create a signed long from the first "n" bytes of the given array, ordered from MSB to LSB
	*
	*	@param	rawData	The bytes to make the long value from.
	*	@param	n	The number of bytes to use (n must be less than or equal to 8, the number of bytes in a long value).
	*
	*	@return	A long value corresponding to the given array of bytes as a big-endian value.
	*/
	public static long makeLongBE(final byte[] rawData, int n)
	{
		n = java.lang.Math.min(n, rawData.length);
		if (n > LONG_BYTES)
			throw new RuntimeException("Need 8 bytes or fewer to make a long value");

		long val = 0;
		for (int i = 0; i < n; ++i)
			val = (val << Byte.SIZE) | (rawData[i] & 0xff);

		return val;
	}

	/**	Create a signed long from the given array of up to eight bytes, ordered from MSB to LSB.
	*
	*	@param	rawData	The bytes to make the long value from.
	*
	*	@return	A long value corresponding to the given array of bytes as a big-endian value.
	*/
	public static long makeLongBE(byte[] rawData)
	{
		return makeLongBE(rawData, LONG_BYTES);
	}

	/**	Create a signed long from the first "n" bytes of the given array, ordered from LSB to MSB.
	*
	*	@param	rawData	The bytes to make the long value from.
	*	@param	n	The number of bytes to use (n must be less than or equal to 8, the number of bytes in a long value).
	*
	*	@return	A long value corresponding to the given array of bytes as a little-endian value.
	*/
	public static long makeLongLE(byte[] rawData, int n)
	{
		n = java.lang.Math.min(n, rawData.length);
		if (n > LONG_BYTES)
			throw new RuntimeException("Need 8 bytes or fewer to make a long value");

		long val = 0;
		for (int i = n - 1; i >= 0; --i)
			val = (val << Byte.SIZE) | (rawData[i] & 0xff);
		return val;
	}

	/**	Create a signed long from the given array of bytes, ordered from LSB to MSB.
	*
	*	@param	rawData	The bytes to make the long value from.
	*
	*	@return	A long value corresponding to the given array of bytes as a little-endian value.
	*/
	public static long makeLongLE(byte[] rawData)
	{
		return makeLongLE(rawData, LONG_BYTES);
	}

	/**	This is a simplistic test for some of the functions in this class.
	*
	*	@param	args	The command line arguments passed to the test application (ignored).
	*/
	public static void main(String[] args)
	{
		byte[][] a = 
		{
			{ 0x12, 0x34, 0x56, 0x78, (byte)0x9a, (byte)0xbc, (byte)0xde, (byte)0xf0 },
			{ 0x12, 0x34, 0x56, 0x78, (byte)0x9a, (byte)0xbc, (byte)0xde, (byte)0x0f },
			{ 0x00, 0x7a, 0x16, 0x0b, 0x00, 0x00, 0x00, 0x00 }
		};

		for (int i = 0; i < a.length; ++i)
		{
			long be = makeLongBE(a[i]);
			System.out.println("Big Endian:    " + Long.toHexString(be));

			long le = makeLongLE(a[i]);
			System.out.println("Little Endian: " + Long.toHexString(le) + '\n');
		}

		for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE; ++i) {
			byte[] ba = { (byte)i };
			System.out.printf("%d: 0x%02x %s %s\n", i, i & 0xff, ByteUtil.toHexString((byte)i), ByteUtil.createHexByteString(ba));
		}
	}
}
