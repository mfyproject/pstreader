package io.github.jmcleodfoss.pst;

/**	The TableContext class represents a PST Table Context, which is a structure on a B-tree-on-heap. The class itself is not
*	publicly available, but it extends javax.swing.table.AbstractTableModel, which provides a usable public interface.
*
*	@see	"[MS-PST] Outlook Personal Folders (.pst) File Format v20110608, section 2.3.4"
*	@see	<a href="http://msdn.microsoft.com/en-us/library/ff386198(v=office.12).aspx">Table Context (TC) (MSDN)</a>
*/
public class TableContext extends javax.swing.table.AbstractTableModel {

	/**	The serialVersionUID is required because the base class is serializable. */
	private static final long serialVersionUID = 1L;

	/**	Logger for debugging TableContexts */
	static java.util.logging.Logger logger = Debug.getLogger("io.github.jmcleodfoss.pst.TableContext");

	/**	The TCInfo class represents the PST file TCINFO structure, and contains table context info for a table context.
	*
	*	@see	"[MS-PST] Outlook Personal Folders (.pst) File Format v20110608, section 2.3.4.1"
	*	@see	<a href="http://msdn.microsoft.com/en-us/library/ff385913(v=office.12).aspx">TCINFO (MSDN)</a>
	*/
	private static class TCInfo {

		/**	Index into rgib table of the ending offset of 4 and 8 byte data values. */
		private static final int TCI_4b = 0;

		/**	Index into rgib table of the ending offset of 2 byte data values. */
		private static final int TCI_2b = 1;

		/**	Index into rgib table of the ending offset of 1 byte data values. */
		private static final int TCI_1b = 2;

		/**	Index into rgib table of the ending offset of the Cell Existence Block. */
		private static final int TCI_bm = 3;

		private static final String nm_bType = "bType";
		private static final String nm_cCols = "cCols";
		private static final String nm_rgib = "rgib";
		private static final String nm_hidRowIndex = "hidRowIndex";
		private static final String nm_hnidRows = "hnidRows";
		private static final String nm_hidIndex = "hidIndex";
		private static final String nm_rgTColDescr = "rgTColDescr";

		/**	The fields in the input stream which make up the Table Context Info. */
		private static final DataDefinition[] fields = {
			new DataDefinition(nm_bType, DataType.integer8Reader, true),
			new DataDefinition(nm_cCols, DataType.integer8Reader, true),
			new DataDefinition(nm_rgib, new DataType.SizedInt16Array(4), true),
			new DataDefinition(nm_hidRowIndex, DataType.hidReader, true),
			new DataDefinition(nm_hnidRows, DataType.hidReader, true),
			new DataDefinition(nm_hidIndex, DataType.hidReader, false) // deprecated
		};

		/**	The heap index of the row ID B-tree-on-heap. */
		private final HeapOnNode.HID hidRowIndex;

		/**	The rows of the table. */
		private final HeapOnNode.HID hnidRows;

		/**	The column descriptions. */
		private final TColDescr[] columnDescription;

		/**	Ending offsets of 4/8, 2, and 1 byte values and the cell existence block. */
		private final short[] endingOffsets;

		/**	Description of each field. */
		private final DataDefinition[] rowFields;

		/**	The data types of the columns stored in HNIDs. */
		private final DataType[] hnidTypes;

		/**	Create a TCInfo object by reading in the required information from the data inputstream.
		*
		*	@param	stream	The input data stream from which to read the TCINFO structure.
		*
		*	@throws	NotTableContextNodeException	A node which is not a table context node was found while building the table context information object.
		*	@throws UnknownClientSignatureException	An unknown client signature was found while building the table context information object.
		*	@throws java.io.IOException		An I/O exception was encountered while reading the data for the table context information obkect.
		*/
		@SuppressWarnings("unchecked")
		private TCInfo(java.nio.ByteBuffer stream)
		throws
			NotTableContextNodeException,
			UnknownClientSignatureException,
			java.io.IOException
		{
			DataContainer dc = new DataContainer();
			dc.read(stream, fields);

			ClientSignature clientSignature = new ClientSignature((Byte)dc.get(nm_bType));
			if (!clientSignature.equals(ClientSignature.TableContext))
				throw new NotTableContextNodeException(clientSignature);

			int numColumns = dc.getUInt8(nm_cCols);
			columnDescription = new TColDescr[numColumns];
			for (int i = 0; i < numColumns; ++i)
				columnDescription[i] = new TColDescr(stream);

			java.util.Arrays.sort(columnDescription, new TColDescr.Comparator());

			hidRowIndex = (HeapOnNode.HID)dc.get(nm_hidRowIndex);
			hnidRows = (HeapOnNode.HID)dc.get(nm_hnidRows);
			endingOffsets = (short[])dc.get(nm_rgib);

			rowFields = new DataDefinition[numColumns+1];
			hnidTypes = new DataType[numColumns];
			for (int i = 0; i < numColumns; ++i) {
				if (storedInHNID(columnDescription[i])){
					rowFields[i] = new DataDefinition(fieldName(i), DataType.hidReader, true);
					hnidTypes[i] = DataType.definitionFactory(columnDescription[i].propertyType());
				} else
					rowFields[i] = new DataDefinition(fieldName(i), DataType.definitionFactory(columnDescription[i].propertyType()), true);
			}
			rowFields[numColumns] = new DataDefinition(cellExistenceBitmapFieldName(), new DataType.SizedByteArray((numColumns+7)/8), true);
		}

		/**	Create the name of the field containing the Cell Existence Bitmap (which is the last field in the row). Note
		*	that this provides the field name in a format consistent with those of the other fields, which allows simple
		*	construction of all field names knowing the number of columns.
		*
		*	@return	The name of the cell existence bitmap field.
		*/
		private String cellExistenceBitmapFieldName()
		{
			return fieldName(columnDescription.length);
		}

		/**	Create a generic field name with the given value as a suffix.
		*
		*	@param	i	The field index for which to create the field name.
		*
		*	@return	A generic synthesized field name based on the given field index.
		*/
		private String fieldName(int i)
		{
			return "fld_" + i;
		}

		/**	Provide a representation describing this TCInfo object. This is typically used for debugging.
		*
		*	@return	A string describing this TCInfo object, which includes descriptions of all columns.
		*/
		@Override
		public String toString()
		{
			StringBuilder s = new StringBuilder();
			s.append("hidRowIndex ");
			s.append(hidRowIndex);
			s.append(" columns ");
			s.append(Integer.toString(columnDescription.length));
			s.append(" hnidRows ");
			s.append(hnidRows);
			for (TColDescr tcd : columnDescription) {
				s.append('\n');
				s.append('\t');
				s.append(tcd);
			}
			s.append("\nData block ending offsets:");
			for (int i : endingOffsets) {
				s.append(' ');
				s.append(i);
			}
			return s.toString();
		}

		/**	Is the data described by the given column description object stored directly in the table, or in the
		*	HNID stored in the table? Note that any field with a size of less than 4 is stored directly in the table.
		*
		*	@param	cd	The column description to check.
		*
		*	@return	true if the field referred to by the given column description object is stored in an HID, false if it
		*		is stored directly in the table.
		*/
		static boolean storedInHNID(TColDescr cd)
		{
			if (cd.width <= 4)
				return storedInHID(cd.propertyType());

			return false;
		}
	}

	/**	The TColDescr class holds a single table column description object.
	*
	*	@see	"[MS-PST] Outlook Personal Folders (.pst) File Format v20110608, section 2.3.4.2"
	*	@see	<a href="http://msdn.microsoft.com/en-us/library/ff385755(v=office.12).aspx">TCINFO (MSDN)</a>
	*/
	static class TColDescr {

		/**	The Comparator class permits the list of fields to sorted by row offset.
		*/
		static class Comparator implements java.util.Comparator<TColDescr> {

			/**	Compare the two TColDescr objects.
			*
			*	@param	a	One TColDescr object for comparison.
			*	@param	b	The other TColDescr object for comparison.
			*
			*	@return	The difference in the column offsets of a and b, to ensure that the item with the larger
			*		offset is sorted later.
			*/
			public int compare(TColDescr a, TColDescr b)
			{
				return a.columnOffset - b.columnOffset;
			}
		}

		private static final String nm_tag = "tag";
		private static final String nm_ibData = "ibData";
		private static final String nm_cbData = "cbData";
		private static final String nm_iBit = "iBit";

		/**	The fields in the input stream which make up the table column description. */
		private static final DataDefinition[] fields = {
			new DataDefinition(nm_tag, DataType.integer32Reader, true),
			new DataDefinition(nm_ibData, DataType.integer16Reader, true),
			new DataDefinition(nm_cbData, DataType.integer8Reader, true),
			new DataDefinition(nm_iBit, DataType.integer8Reader, true)
		};

		/**	Each column has an associated 32-bit tag. */
		private final int tag;

		/**	The offset of the data for this column within a row. */
		private final int columnOffset;

		/**	The number of bytes of data in this column. */
		private final int width;

		/**	The cell existence bitmap. */
		private final byte cellExistenceBitmapIndex;

		/**	Create a TColDescr object from date read in from the input datastream.
		*
		*	@param	stream	The input data stream from which to read the column description.
		*
		*	@throws	java.io.IOException	An I/O error was encounted while reading the data for this column description.
		*/
		private TColDescr(java.nio.ByteBuffer stream)
		throws
			java.io.IOException
		{
			DataContainer dc = new DataContainer();
			dc.read(stream, fields);
			tag = (Integer)dc.get(nm_tag);
			columnOffset = (Short)dc.get(nm_ibData);
			width = dc.getUInt8(nm_cbData);
			cellExistenceBitmapIndex = (Byte)dc.get(nm_iBit); 
		}

		/**	Get the data type for this column description tag.
		*
		*	@return	The property type as masked off from the column tag.
		*/
		private short propertyType()
		{
			return (short)(tag & 0xffff);
		}

		/**	Provide a String representation of this TCInfo object. This is typically used for debugging.
		*
		*	@return	A description of this TColDescr object.
		*/
		@Override
		public String toString()
		{
			String propertyName = PropertyTagName.name(tag);
			return String.format("tag 0x%08x (%s) offset into row %d width %d CEB index %d", tag, propertyName, columnOffset, width, cellExistenceBitmapIndex);
		}
	}

	/**	The Iterator class provides a means for returning a subset of columns from the table for each row.
	*/
	private class Iterator implements java.util.Iterator<Object> {

		/**	The next row to return. */
		private int row;

		/**	Construct an iterator to return the given columns for each row. */
		public Iterator()
		{
			row = 0;
		}

		/**	Indicate whether the "next" function will return anything.
		*
		*	@return	true if there is another row to return, false if there are no more rows to return.
		*/
		public boolean hasNext()
		{
			return !isEmpty() && row < rows.length;
		}

		/**	Return the next row.
		*
		*	@return	The next row, as an array of objects.
		*/
		public Object next()
		{
			return rows[row++];
		}

		/**	The remove function is not supported by the TableContext iterator. */
		public void remove()
		{
			throw new UnsupportedOperationException("remove not suported");
		}
	}

	/**	The TCINFO (Table Context Info) structure for this table context */
	private final TCInfo info;

	/**	The RowIndex for this table context. */
	private final BTreeOnHeap rowIndex;

	/**	The row data */
	private Object[][] rows;

	/**	Create a table context from the given BID.
	*
	*	@param	nodeDescr	Description of the node as found in the block or sub-node B-tree.
	*	@param	bbt		The PST file's block B-tree.
	*	@param	pstFile		The PST file data stream, header, etc.
	*
	* 	@throws	NotHeapNodeException			The leaf is not a heap node
	* 	@throws NotTableContextNodeException		A node without the Table Context client signature was found while building the table context.
	* 	@throws UnknownClientSignatureException		The Client Signature was not recognized
	* 	@throws UnparseableTableContextException	The table content could not be interpreted
	* 	@throws java.io.IOException			There was an I/O error reading the table.
	*/
	public TableContext(LPTLeaf nodeDescr, BlockMap bbt, PSTFile pstFile)
	throws
		NotHeapNodeException,
		NotTableContextNodeException,
		UnknownClientSignatureException,
		UnparseableTableContextException,
		java.io.IOException
	{
		this(nodeDescr, new HeapOnNode(bbt.find(nodeDescr.bidData), bbt, pstFile), bbt, pstFile);
	}

	/**	Create a TableContext object from the given heap-on-node. This should only be used when the Heap-On-Node has already
	*	been found for purposes other than building the Table context.
	*
	*	@param	nodeDescr	Description of the node as found in the block or sub-node B-tree.
	*	@param	hon		The heap-on-node on which this table context is defined.
	*	@param	bbt		The PST file's block B-tree.
	*	@param	pstFile		The PST file data stream, header, etc.
	*
	* 	@throws NotTableContextNodeException		A node without the Table Context client signature was found while building the table context.
	* 	@throws UnknownClientSignatureException		The Client Signature was not recognized
	* 	@throws UnparseableTableContextException	The table content could not be interpreted
	* 	@throws java.io.IOException			There was an I/O error reading the table.
	*/
	TableContext(LPTLeaf nodeDescr, HeapOnNode hon, BlockMap bbt, PSTFile pstFile)
	throws
		NotTableContextNodeException,
		UnknownClientSignatureException,
		UnparseableTableContextException,
		java.io.IOException
	{
		info = new TCInfo(PSTFile.makeByteBuffer(hon.userRootHeapData()));

		// Note that TCInfo.toString is relatively expensive. Only call it if we really need it.
		if (logger.isLoggable(java.util.logging.Level.INFO))
			logger.log(java.util.logging.Level.INFO, "TC Info\n-------\n" + info);

		rowIndex = new BTreeOnHeap(hon, info.hidRowIndex, pstFile);
		int numRows = rowIndex.numLeafNodes();
		rows = new Object[numRows][];

		if (numRows == 0)
			return;

		if (info.columnDescription.length == 0)
			return;

		SubnodeBTree sbt = nodeDescr.bidSubnode.isNull() ? null : new SubnodeBTree(nodeDescr.bidSubnode, bbt, pstFile);
		if (info.hnidRows.type == NID.HID) {
			if (hon.heapData(info.hnidRows).length != expectedSize())
				throw new UnparseableTableContextException("Not enough bytes for row data: found " + hon.heapData(info.hnidRows).length + ", expected " + expectedSize());

			readRows(hon, info.columnDescription.length, hon.heapData(info.hnidRows), sbt, bbt, pstFile);
		} else if (info.hnidRows.type == NID.LTP) {
			SLEntry slEntry = (SLEntry)sbt.find(info.hnidRows.key());
			assert slEntry != null;

			BBTEntry bbtEntry = bbt.find(slEntry.bidData);
			assert bbtEntry != null;

			BlockBase b = BlockBase.read(bbtEntry, bbt, pstFile);
			readRows(hon, info.columnDescription.length, b.iterator(), sbt, bbt, pstFile);
		} else {
			assert false: "Unknown HNID node type " + info.hnidRows;
		}
	}

	/**	Returns true if the Cell Existence Bitmap indicates this column exists, and false otherwise.
	*
	*	@param	ceb	The cell existence block for the row currently being processed.
	*	@param	column	The column of the table in which the field being processed lies.
	*
	*	@return	true if the cell existence bitmap indicates that the field is present, false if the cell existens bitmap
	*			indicates it is absent.
	*/
	private boolean cellExists(byte[] ceb, int column)
	{
		int cebIndex = info.columnDescription[column].cellExistenceBitmapIndex;
		return (ceb[cebIndex/8] & (1 << (7 - cebIndex % 8))) != 0;
	}

	/**	Get the column index for the given tag, if present.
	*
	*	@param	tag	The tag to look for in the table context's column list.
	*
	*	@return	The column index for the given tag, if present, or -1 if the tag was not found.
	*/
	private int columnIndex(int tag)
	{
		for (int i = 0; i < info.columnDescription.length; ++i) {
			if (tag == info.columnDescription[i].tag)
				return i;
		}
		return -1;
	}

	/**	The expected number of bytes in the row data heap entry.
	*
	*	@return	The expected number of bytes in a row data heap entry, based on the number of rows and the number of sum of the
	*		number of bytes in all columns.
	*/
	private int expectedSize()
	{
		return rows.length * DataDefinition.size(info.rowFields);
	}

	/**	Get the value for the given tag, if it exists, for the given row.
	*
	*	@param	row	The row to return information for.
	*	@param	tag	The tag indicates the column to return.
	*
	*	@return	The value for the given tag in the given row, if any, otherwise null.
	*/
	public Object get(int row, int tag)
	{
		if (row > getRowCount())
			return null;

		final int column = columnIndex(tag);
		if (column == -1)
			return null;
		return rows[row][column];
	}

	/**	Get the number of data columns in the table.
	*
	*	@return	The number of data columns (i.e. excluding the cell existence bitmap) in this table context.
	*/
	public int getColumnCount()
	{
		// Note: don't include the Cell Existence Bitmap!
		return (info == null || info.columnDescription == null) ? 0 : info.columnDescription.length - 1;
	}

	/**	Get the name of the given column for use as a table header.
	*
	*	@param	column	The column to retrieve the header for.
	*
	*	@return	The column name, as a property ID. Note that this function returns a generic name for named properties.
	*/
	public String getColumnName(int column)
	{
		return PropertyTagName.name(info.columnDescription[column].tag);
	}

	/**	Obtain a ByteBuffer from which the raw data for the given propertyID may be read.
	*
	*	@param	propertyTag	The property ID of the tag to read.
	*	@param	data		The raw data for this cell in the table.
	*	@param	hon		The heap-on-node in which to look up data stored in an HID.
	*
	*	@return	A ByteBuffer form which the data may be read.
	*
	*	@throws	java.io.UnsupportedEncodingException	An unsupported text encouding was found while reading in a String for this table.
	*
	*	@see	io.github.jmcleodfoss.pst.BTreeOnHeap#getData
	*	@see	io.github.jmcleodfoss.pst.PropertyContext#getData
	*/
	static java.nio.ByteBuffer getData(int propertyTag, byte[] data, HeapOnNode hon)
	throws
		java.io.UnsupportedEncodingException
	{
		java.nio.ByteBuffer dataBuffer = PSTFile.makeByteBuffer(data);

		if (storedInHID(propertyTag & 0xffff)) {
			DataType hidReader = DataType.hidReader;
			HeapOnNode.HID hid = (HeapOnNode.HID)hidReader.read(dataBuffer);
			if (!hon.validHID(hid))
				return null;

			if (hid.isHID()) {
				if (hon.heapData(hid) == null)
					return null;

				return java.nio.ByteBuffer.wrap(hon.heapData(hid)).asReadOnlyBuffer();
			}
		}

		return java.nio.ByteBuffer.wrap(data).asReadOnlyBuffer();
	}

	/**	Get the number of rows in the table.
	*
	*	@return	The number of rows in this table context.
	*/
	public int getRowCount()
	{
		return rows.length;
	}

	/**	Return the value of the specified cell.
	*
	*	@param	row	The row index of the cell to retrieve the value of.
	*	@param	column	The column index of the cell to retrieve the value of.
	*
	*	@return	The value of the given cell, or null if the row for the given row index is empty.
	*/
	public Object getValueAt(int row, int column)
	{
		return rows[row] == null ? null : rows[row][column];
	}


	/**	No cells are editable.
	*
	*	@param	row	The row index of the cell to retrieve the value of.
	*	@param	column	The column index of the cell to retrieve the value of.
	*
	*	@return	false, always.
	*/
	public boolean isCellEditable(int row, int column)
	{
		return false;
	}

	/**	Is this table context object empty?
	*
	*	@return	true if the number of rows in this table context is 0, false if it is non-zero.
	*/
	private boolean isEmpty()
	{
		return rows.length == 0;
	}

	/**	Obtain an iterator for the rows of this TableContext which returns all columns. cf specifiedColumnIterator
	*
	*	@return	The array of objects which make up this row of the TableContext.
	*/
	@SuppressWarnings("unchecked")
	java.util.Iterator<Object> iterator()
	{
		return isEmpty() ? EmptyIterator.iterator : new Iterator();
	}

	/**	Read data for all rows from a block of bytes of raw data. This is used to read HID table contexts.
	*
	*	@param	hon		The heap-on-node containing this table context.
	*	@param	numColumns	The number of columns in this table context (excluding the cell existence bitmap).
	*	@param	data		The raw row data.
	*	@param	sbt		The sub-node B-tree for the table context (where the HID data is to be found).
	*	@param	bbt		The PST file's block B-tree.
	*	@param	pstFile		The PST file's input data stream, header, etc.
	*
	*	@throws	java.io.IOException	An I/O error was encountered while reading in the rows for the table context.
	*/
	private void readRows(HeapOnNode hon, int numColumns, byte[] data, SubnodeBTree sbt, BlockMap bbt, PSTFile pstFile)
	throws
		java.io.IOException
	{
		java.nio.ByteBuffer rowStream = PSTFile.makeByteBuffer(data);

		int rowWidth = info.endingOffsets[TCInfo.TCI_bm];
		final int rowsPerBlock = (BlockBase.MAX_BLOCK_BYTES - BlockTrailer.size(pstFile))/rowWidth;
		final int nPaddingBytes = BlockBase.MAX_BLOCK_BYTES - BlockTrailer.size(pstFile) - rowsPerBlock*rowWidth;
		logger.log(java.util.logging.Level.INFO, String.format("Rows/Block %d padding at end of block %d", rowsPerBlock, nPaddingBytes));
		int r;
		for (r = 0; r < rows.length; ++r) {
			if (r > 0 && r % rowsPerBlock == 0)
				rowStream.position(rowStream.position() + nPaddingBytes);
			rows[r] = readRow(rowStream, numColumns, r, sbt, bbt, hon, pstFile);

		}
	}

	/**	Read data for all rows from blocks of bytes returned by an iterator. This is used to read LTP table contexts.
	*
	*	@param	hon		The heap-on-node containing this table context.
	*	@param	numColumns	The number of columns in this table context (excluding the cell existence bitmap).
	*	@param	iterator	An iterator through the blocks which comprise an LTP table context.
	*	@param	sbt		The sub-node B-tree for the table context (where the HID data is to be found).
	*	@param	bbt		The PST file's block B-tree.
	*	@param	pstFile		The PST file's input data stream, header, etc.
	*
	*	@throws	java.io.IOException	An I/O error was encountered while reading in the rows for the table context.
	*/
	private void readRows(HeapOnNode hon, int numColumns, java.util.Iterator<java.nio.ByteBuffer> iterator, SubnodeBTree sbt, BlockMap bbt, PSTFile pstFile)
	throws
		java.io.IOException
	{
		int rowWidth = info.endingOffsets[TCInfo.TCI_bm];
		int r = 0;

		while (iterator.hasNext()) {
			java.nio.ByteBuffer rowStream = iterator.next();
			while (rowStream.remaining() >= rowWidth) {
				if (r >= rows.length)
					throw new RuntimeException("Too much data for " + rows.length + " rows");
				rows[r++] = readRow(rowStream, numColumns, r, sbt, bbt, hon, pstFile);
			}
		}
	}

	/**	Read a single row from the TableContext
	*
	*	@param	rowStream	The raw data for this row, as a ByteBuffer with the correct endianness.
	*	@param	numColumns	The number of columns in this table context (excluding the cell existence bitmap)
	*	@param	r		The index of this row (used only for diagnostic logging).
	*	@param	sbt		This table context's sub-node B-tree.
	*	@param	bbt		The PST file's block B-tree.
	*	@param	hon		The heap-on-node containing this table context.
	*	@param	pstFile		The PST file input data stream, header, etc.
	*
	*	@return	The data in the row given by rowStream, parsed into the appropriate PST data types.
	*
	*	@throws	java.io.IOException	An I/O error was encountered while reading the data for this table context row.
	*/
	private Object[] readRow(java.nio.ByteBuffer rowStream, int numColumns, int r, SubnodeBTree sbt, BlockMap bbt, HeapOnNode hon, PSTFile pstFile)
	throws
		java.io.IOException
	{
		DataContainer dc = new DataContainer(info.rowFields.length);

		dc.read(rowStream, info.rowFields);
		byte[] cellExistenceBitmap = (byte[])dc.get(info.rowFields[info.rowFields.length-1].name);

		if (logger.isLoggable(java.util.logging.Level.INFO))
			logger.log(java.util.logging.Level.INFO, String.format("%d: CEB %s", r, ByteUtil.createHexByteString(cellExistenceBitmap)));

		Object[] row = new Object[numColumns];
		for (int c = 0; c < numColumns; ++c) {
			Object fieldData = dc.get(info.rowFields[c].name);

			if (!cellExists(cellExistenceBitmap, c)) {
				if (logger.isLoggable(java.util.logging.Level.INFO))
					logger.log(java.util.logging.Level.INFO, String.format("(%d, %d): empty %s", r, c, fieldData.toString()));
				continue;
			}

			if (logger.isLoggable(java.util.logging.Level.INFO))
				logger.log(java.util.logging.Level.INFO, String.format("(%d, %d) %s 0x%08x: %s", r, c, info.fieldName(c), info.columnDescription[c].tag, fieldData.toString()));

			if (!(fieldData instanceof HeapOnNode.HID)) {
				row[c] = fieldData;
				continue;
			}

			HeapOnNode.HID hid =  (HeapOnNode.HID)fieldData;

			if (!hon.validHID(hid)) {
				row[c] = null;
				continue;
			}

			if (!hid.isHID()) {
				BBTEntry bbtEntry = (BBTEntry)sbt.find(((NID)hid).key());
				BlockBase block = BlockBase.read(bbtEntry, bbt, pstFile);
				java.nio.ByteBuffer bBlock = PSTFile.makeByteBuffer(block.data());
				row[c] = info.rowFields[c].description.read(bBlock);
				continue;
			}

			if (hon.heapData(hid) == null) {
				row[c] = null;
				continue;
			}

			java.nio.ByteBuffer bHeapData = PSTFile.makeByteBuffer(hon.heapData(hid));
			row[c] = info.hnidTypes[c].read(bHeapData);
		}
		return row;
	}

	/**	Are objects of the given property type stored within the tree itself, or in an HID denoted by the leaf element?
	*
	*	@param	propertyType	The propery type to check to see whether it is stored directly in the table or in an HID.
	*
	*	@return	true if the given property type is stored in an HID, false if it store in directly in the table.
	*/
	static boolean storedInHID(int propertyType)
	{
		switch (propertyType & 0xffff) {
		case DataType.OBJECT:
		case DataType.STRING:
		case DataType.STRING_8:
		case DataType.BINARY:
		case DataType.GUID:
		/* The PST document implies that MULTIPLE_INTEGER_32 is not kept in an HID. */
		case DataType.MULTIPLE_INTEGER_32:
		case DataType.MULTIPLE_STRING:
		case DataType.MULTIPLE_BINARY:
			return true;
		}
		
		return false;
	}

	/**	Obtain a String representation of this table context.
	*
	*	@return	A string representation of the table context, showing property ID and value.
	*/
	@Override
	public String toString()
	{
		if (isEmpty())
			return "Empty TableContext";

		StringBuilder s = new StringBuilder(info + "\n" + rowIndex + "\n");
		for (Object[] row : rows) {
			if (row != null)
			for (int c = 0; c < row.length; ++c) {
				s.append("\n" + PropertyTagName.name(info.columnDescription[c].tag) + ": ");
				if (row[c] == null)
					s.append("empty");
				else if (row[c] instanceof Byte)
					s.append("0x" + Integer.toHexString((Byte)row[c]));
				else if (row[c] instanceof Short)
					s.append("0x" + Integer.toHexString((Short)row[c]));
				else if (row[c] instanceof Integer)
					s.append("0x" + Integer.toHexString((Integer)row[c]));
				else if (row[c] instanceof Long)
					s.append("0x" + Long.toHexString((Long)row[c]));
				else if (row[c] instanceof byte[])
					s.append(ByteUtil.createHexByteString((byte[])row[c]));
				else
					s.append(row[c]);
			}
			s.append("\n");
		}
		return s.toString();
	}

	/**	Test the TableContext class by reading in the first node containing a table context, extracting the table, and printing
	*	it out.
	*
	*	@param	args	The command line arguments to the test application.
	*/
	public static void main(String[] args)
	{
		if (args.length < 1) {
			System.out.println("use:\n\tjava io.github.jmcleodfoss.pst.TableContext pst-filename [log-level]");
			System.out.println("\nNote that log-level applies only to construction of the TableContext object.");
			System.exit(1);
		}

		try {
			java.util.logging.Level logLevel = args.length >= 2 ? Debug.getLogLevel(args[1]) : java.util.logging.Level.OFF;
			logger.setLevel(logLevel);

			PSTFile pstFile = new PSTFile(new java.io.FileInputStream(args[0]));
			BlockBTree bbt = new BlockBTree(0, pstFile.header.bbtRoot, pstFile);
			NodeBTree nbt = new NodeBTree(0, pstFile.header.nbtRoot, pstFile);

			OutputSeparator separator = new OutputSeparator();
			java.util.Iterator<BTreeNode> iterator = nbt.iterator();
			while (iterator.hasNext()) {
				NBTEntry nodeDescr = (NBTEntry)iterator.next();
				if (nodeDescr.nid.type == NID.INTERNAL)
					continue;
				BBTEntry dataBlock = bbt.find(nodeDescr.bidData);
				if (dataBlock != null) {
					try {
						HeapOnNode hon = new HeapOnNode(dataBlock, bbt, pstFile);
						if (!hon.containsData())
							continue;
						if (hon.clientSignature().equals(ClientSignature.TableContext)) {
							if (!logger.isLoggable(java.util.logging.Level.OFF))
								separator.emit(System.out);
							if (logger.isLoggable(java.util.logging.Level.FINE))
								logger.log(java.util.logging.Level.FINE, "\nHeapOnNode\n---------\n" + hon);
							TableContext tc = new TableContext(nodeDescr, hon, bbt, pstFile);

							if (!logger.isLoggable(java.util.logging.Level.OFF)) {
								System.out.println("Node " + nodeDescr + "\nTableContext\n------------\n" + tc);
								if (tc.isEmpty())
									continue;

//								java.util.Iterator<java.util.Map.Entry<Integer, Object>> propertyIterator = tc.iterator();
//								while (propertyIterator.hasNext()) {
//									java.util.Map.Entry<Integer, Object> entry = propertyIterator.next();
//									System.out.printf("0x%08x %s\n", entry.getKey(), pst.propertyName(entry.getKey()));
//								}
								if (!logger.isLoggable(java.util.logging.Level.FINE))
									tc.rowIndex.outputString(System.out, new StringBuilder("rowIndex"));
							}
						}
					} catch (final NotHeapNodeException e) {
						// This is expected; we have no way to find out whether a node contains a heap-on-node until we start reading it.
						continue;
					} catch (final UnknownClientSignatureException e) {
						logger.log(java.util.logging.Level.WARNING, nodeDescr + "\n\t" + e.toString());
						e.printStackTrace(System.out);
					} catch (final UnparseableTableContextException e) {
						logger.log(java.util.logging.Level.WARNING, nodeDescr + "\n]t" + e.toString());
						e.printStackTrace(System.out);
					}
				}
			}
		} catch (final Exception e) {
			e.printStackTrace(System.out);
		}
	}
}
