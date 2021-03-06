package io.github.jmcleodfoss.pst;

/**	The XBlock class represents the XBlock and XXBlock block containers within a PST file.
*
*	@see	io.github.jmcleodfoss.pst.BlockBase
*	@see	io.github.jmcleodfoss.pst.SimpleBlock
*	@see	"[MS-PST] Outlook Personal Folders (.pst) File Format v20110608, sections 2.2.8.3.2"
*	@see	<a href="http://msdn.microsoft.com/en-us/library/ff386805(v=office.12).aspx">Block Types (MSDN)</a>
*	@see	<a href="http://msdn.microsoft.com/en-us/library/ff387544(v=office.12).aspx">Data Blocks (MSDN)</a>
*	@see	<a href="http://msdn.microsoft.com/en-us/library/ff385886(v=office.12).aspx">Data Tree (MSDN)</a>
*	@see	<a href="http://msdn.microsoft.com/en-us/library/ff386165(v=office.12).aspx">XBLOCK (MSDN)</a>
*	@see	<a href="http://msdn.microsoft.com/en-us/library/ff385051(v=office.12).aspx">XXBLOCK (MSDN)</a>
*/
class XBlock extends BlockBase {

	/**	An iterator which returns a ByteBuffer view of the underlying data block. */
	private class Iterator implements java.util.Iterator<java.nio.ByteBuffer> {

		/**	The underlying iterator - this class is just a thin wrapper around it to return the SimpleBlock's data as a
		*	ByteBuffer rather than the SimpleBlock itself.
		*/
		private java.util.Iterator<SimpleBlock> blockIterator;

		/**	Construct an iterator from the XBlock blockList vector. */
		Iterator()
		{
			blockIterator = blockList.iterator();
		}

		/**	Is there another value to return?
		*
		*	@return	true if there is another SimpleBlock in the list, false otherwise.
		*/
		public boolean hasNext()
		{
			return blockIterator.hasNext();
		}

		/**	Retrieve the next value.
		*
		*	@return	The data for the next SimpleBlock in the list, as a ByteBuffer.
		*/
		public java.nio.ByteBuffer next()
		{
			return PSTFile.makeByteBuffer(blockIterator.next().data());
		}

		/**	The remove function is not supported by the XBlock iterator. */
		public void remove()
		{
			throw new UnsupportedOperationException("remove not suported");
		}
	}

	private static final String nm_btype = "btype";
	private static final String nm_cLevel = "cLevel";
	private static final String nm_cEnt = "cEnt";
	private static final String nm_lcbTotal = "lcbTotal";
	private static final String nm_rgbid = "rgbid";

	/**	Descriptions of the fields which make up the XBLOCK and XXBLOCK information. */
	private static final DataDefinition[] data_fields = {
		new DataDefinition(nm_btype, DataType.integer8Reader, true),
		new DataDefinition(nm_cLevel, DataType.integer8Reader, true),
		new DataDefinition(nm_cEnt, DataType.integer16Reader, true),
		new DataDefinition(nm_lcbTotal, DataType.integer32Reader, true)
	};

	/**	The BIDs of the sub-blocks. This is saved for {link #toString} only. */
	private final BID[] bid;

	/**	The total amount of data in all sub-blocks */
	private final int dataBytes;

	/**	The sub-blocks in this multi-block structure. */
	final java.util.Vector<SimpleBlock> blockList;

	/**	Create an XBlock/XXBlock from the given block B-tree entry.
	*
	*	@param	entry	The block B-tree entry describing the root of this XBLOCK/XXBLOCK tree structure.
	*	@param	bbt	The PST file's block B-tree (required to find the child blocks).
	*	@param	pstFile	The PST file's input stream, etc.
	*
	*	@throws	java.io.IOException	An I/O exception was encountered when reading the XBLOCK / XXBLOCK data.
	*/
	XBlock(final BBTEntry entry, final BlockMap bbt, PSTFile pstFile)
	throws
		java.io.IOException
	{
		pstFile.position(entry.bref.ib.ib);

		DataContainer dc = new DataContainer();
		dc.read(pstFile.mbb, data_fields);

		final byte type = (Byte)dc.get(nm_btype);
		if (type != 0x01)
			throw new RuntimeException("Block " + entry + " is not an XBlock/XXBlock, type " + Integer.toHexString(type));

		final byte level = (Byte)dc.get(nm_cLevel);
		if (level != 1 & level != 2)
			throw new RuntimeException("XBlock/XXBlock level must be 1 or 2, found " + level);

		final int numEntries = (Short)dc.get(nm_cEnt);

		DataDefinition bidField = new DataDefinition(nm_data, DataType.BIDFactory(pstFile.unicode()), true);
		BID[] bid = new BID[numEntries];
		for (int i = 0; i < numEntries; ++i) {
			dc.read(pstFile.mbb, bidField);
			bid[i] = (BID)dc.get(nm_data);
		}
		this.bid = bid;

		final int blockSize = blockSize(entry.numBytes, pstFile);
		DataDefinition paddingField = new DataDefinition(nm_padding, new DataType.SizedByteArray(blockSize-entry.numBytes-BlockTrailer.size(pstFile)), false);
		dc.read(pstFile.mbb, paddingField);
		final BlockTrailer trailer = new BlockTrailer(pstFile);

		if (level == 1) {
			blockList = readXBlock(numEntries, bid, bbt, pstFile);
		} else {
			java.util.Vector<XBlock> xblockList = readXXBlock(numEntries, bid, bbt, pstFile);

			int nBlocks = 0;
			for (java.util.Iterator<XBlock> xIter = xblockList.iterator(); xIter.hasNext(); )
				nBlocks += xIter.next().blockList.size();

			java.util.Vector<SimpleBlock> blockList = new java.util.Vector<SimpleBlock>(nBlocks);
			for (java.util.Iterator<XBlock> xIter = xblockList.iterator(); xIter.hasNext(); ) {
				final XBlock xblock = xIter.next();
				for (java.util.Iterator<SimpleBlock> blockIter = xblock.blockList.iterator(); blockIter.hasNext(); )
					blockList.add(blockIter.next());
			}
			this.blockList = blockList;
		}

		int size = 0;
		for (java.util.Iterator<SimpleBlock> bIter = blockList.iterator(); bIter.hasNext(); )
			size += bIter.next().data.length;
		dataBytes = size;
	}

	/**	Retrieve the consolidated data array for this data tree.
	*
	*	@return	An array consisting of the data in all the leaf blocks of the data tree.
	*/
	byte[] data()
	{
		byte[] data = new byte[dataBytes];
		int destOffset = 0;
		java.util.Iterator<SimpleBlock> blockIterator = blockList.iterator();
		while (blockIterator.hasNext()) {
			final SimpleBlock block = blockIterator.next();
			System.arraycopy(block.data, 0, data, destOffset, block.data.length);
			destOffset += block.data.length;
		}
		
		return data;
	}

	/**	Obtain an iterator to iterate through the child blocks.
	*
	*	@return	An iterator through the SimpleBlock objects making up the leaf nodes of this XBLOCK/XXBLOCK structure.
	*/
	java.util.Iterator<java.nio.ByteBuffer> iterator()
	{
		return new Iterator();
	}

	/**	Read in an XBLOCK.
	*
	*	@param	numEntries	The number of child block entries in this XBlock.
	*	@param	bid		The array of BIDs of the blocks.
	*	@param	bbt		The PST file's block B-tree.
	*	@param	pstFile		The underlying PST file's data stream, header, etc.
	*
	*	@return	A vector of SimpleBlock objects.
	*
	*	@throws	java.io.IOException	An I/O exception was encountered while reading in the requested XBlocks.
	*/
	static java.util.Vector<SimpleBlock> readXBlock(final int numEntries, final BID[] bid, final BlockMap bbt, PSTFile pstFile)
	throws
		java.io.IOException
	{
		java.util.Vector<SimpleBlock> blockList = new java.util.Vector<SimpleBlock>(numEntries);
		for (BID b : bid) {
			final BBTEntry blockEntry = bbt.find(b);
			assert blockEntry != null;
			final SimpleBlock block = new SimpleBlock(blockEntry, pstFile);
			blockList.add(block);
		}
		return blockList;
	}

	/**	Read in an XXBLOCK.
	*
	*	@param	numEntries	The number of child XBLOCK entries in this XXBlock.
	*	@param	bid		The array of BIDs of the XBLOCKs.
	*	@param	bbt		The PST file's block B-tree.
	*	@param	pstFile		The underlying PST file's data stream, header, etc.
	*
	*	@return	A vector of XBlock objects.
	*
	*	@throws	java.io.IOException	An I/O exception was encountered while reading in the requested XXBlocks.
	*/
	static java.util.Vector<XBlock> readXXBlock(final int numEntries, final BID[] bid, final BlockMap bbt, PSTFile pstFile)
	throws
		java.io.IOException
	{
		java.util.Vector<XBlock> xblockList = new java.util.Vector<XBlock>(numEntries);
		for (BID b : bid) {
			final BBTEntry blockEntry = bbt.find(b);
			assert blockEntry != null;
			final XBlock xBlock = new XBlock(blockEntry, bbt, pstFile);
			xblockList.add(xBlock);
		}

		return xblockList;
	}

	/**	Obtain a string representation of the XBlock object.
	*
	*	@return	A string representation of the data tree.
	*
	*	@see	#bid
	*/
	@Override
	public String toString()
	{
		String s = String.format("%d bytes in %d data blocks:\n", dataBytes, blockList.size());
		for (int i = 0; i < bid.length; ++i) {
			if (i > 0)
				s += "\n";
			s += bid[i];
		}
		return s;
	}

	/**	Test the XBlock class by traversing the node BTree and getting the XBlocks for each internal data block. This should
	*	also display the XBlocks in each node subtree block, but that's a little tougher.
	*
	*	@param	args	The command line arguments to the test application.
	*/
	public static void main(String[] args)
	{
		if (args.length < 1) {
			System.out.println("use:\n\tjava io.github.jmcleodfoss.pst.XBlock pst-filename [log-level]");
			System.out.println("\nNote that log-level applies only to construction of the NameIDToMap object.");
			System.exit(1);
		}

		try {
			final java.util.logging.Level logLevel = args.length >= 2 ? Debug.getLogLevel(args[1]) : java.util.logging.Level.OFF;
			java.util.logging.Logger logger = java.util.logging.Logger.getLogger("io.github.jmcleodfoss.pst");
			logger.setLevel(logLevel);

			PSTFile pstFile = new PSTFile(new java.io.FileInputStream(args[0]));
			final BlockBTree bbt = new BlockBTree(0, pstFile.header.bbtRoot, pstFile);
			final NodeBTree nbt = new NodeBTree(0, pstFile.header.nbtRoot, pstFile);
	
			java.util.Iterator<BTreeNode> iterator = nbt.iterator();
			while (iterator.hasNext()) {
				final NBTEntry node = (NBTEntry)iterator.next();
				if (node.bidData.fInternal) {
					final BBTEntry block = bbt.find(node.bidData);
					if (block == null) {
						System.out.println("Block for node " + node + " is null");
						continue;
					}
					final XBlock xblock = new XBlock(block, bbt, pstFile);
					System.out.println(xblock);
				}
			}

		} catch (final Exception e) {
			e.printStackTrace(System.out);
		}
	}
}
