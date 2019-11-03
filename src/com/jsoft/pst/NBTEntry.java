package com.jsoft.pst;

/**	The NBTEntry class represents a node B-tree leaf entry.
*
*	@see	com.jsoft.pst.BBTEntry
*	@see	com.jsoft.pst.BTree
*	@see	com.jsoft.pst.NodeBTree
*	@see	com.jsoft.pst.PagedBTree
*	@see	com.jsoft.pst.PagedBTree.BTEntry
*	@see	"[MS-PST] Outlook Personal Folders (.pst) File Format v20110608  Section 2.2.2.7.7.4"
*	@see	<a href="http://msdn.microsoft.com/en-us/library/ff385505(v=office.12).aspx">NBTEntry (Leaf NBT Entry) (MSDN)</a>
*/
public class NBTEntry extends LPTLeaf {

	/**	The serialVersionUID is required because the base class is serializable. */
	private static final long serialVersionUID = 1L;

	private static final String nm_nidParent = "nidParent";

	/**	The Unicode-specific fields in the input stream which make up the node B-tree leaf entry. */
	private static final DataDefinition[] unicode_fields = {
		new DataDefinition(nm_nid, DataType.nidReader, true),
		new DataDefinition(nm_nid_padding, new DataType.SizedByteArray(4), false),
		new DataDefinition(nm_bidData, DataType.bidUnicodeReader, true),
		new DataDefinition(nm_bidSubnode, DataType.bidUnicodeReader, true),
	};

	/**	The size in bytes of the Unicode-specific fields in a node B-tree leaf entry. */
	private static final int SIZE_UNICODE = DataDefinition.size(unicode_fields);

	/**	The ANSI-specific fields in the input stream which make up the node B-tree leaf entry. */
	private static final DataDefinition[] ansi_fields = {
		new DataDefinition(nm_nid, DataType.nidReader, true),
		new DataDefinition(nm_bidData, DataType.bidAnsiReader, true),
		new DataDefinition(nm_bidSubnode, DataType.bidAnsiReader, true),
	};

	/**	The size in bytes of the ANSI-specific fields in a node B-tree leaf entry. */
	private static final int SIZE_ANSI = DataDefinition.size(ansi_fields);

	/**	The fields common to both ANSI and Unicode files in the input stream which make up the node B-tree leaf entry. */
	private static final DataDefinition[] common_fields = {
		new DataDefinition(nm_nidParent, DataType.nidReader, true)
	};

	/**	The size in bytes of the fields common to ANSI and Unicode files in a node B-tree leaf entry. */
	private static final int SIZE_COMMON = DataDefinition.size(common_fields);

	/**	The node ID of the parent node if this node is a child of a folder object. */
	public final NID nidParent;

	/**	Create a node B-tree leaf entry from data read in from the input datastream.
	*
	*	@param	byteBuffer	The data stream from which to read the Node B-tree leaf entry.
	*	@param	context		The context to use when reading the leaf data.
	*/
	NBTEntry(java.nio.ByteBuffer byteBuffer, final PagedBTree.PageContext<BTree, BTreeLeaf> context)
	throws
		java.io.IOException
	{
		super(byteBuffer, context.unicode() ? unicode_fields : ansi_fields, common_fields);

		nidParent = (NID)dc.get(nm_nidParent);
	}

	/**	Obtain the actual size of a node B-tree leaf node as read in from the input datastream.
	*
	*	@param	context	The context to use to find the size (this function uses only the file format information.)
	*
	*	@return	The actual size of a node B-tree leaf node for this file type.
	*/
	public int actualSize(final BTree.Context<BTree, BTreeLeaf> context)
	{
		return SIZE_COMMON + (context.unicode() ? SIZE_UNICODE : SIZE_ANSI);
	}

	/**	{@inheritDoc} */
	public javax.swing.table.TableModel getNodeTableModel()
	{
		final Object[] columnHeadings = {"", ""};
		final Object[][] cells = {
			new Object[]{"NID", nid},
			new Object[]{"Data BID", bidData},
			new Object[]{"Subnode BID", bidSubnode},
			new Object[]{"Parent NID", nidParent}
		};

		return new com.jsoft.swingutil.ReadOnlyTableModel(cells, columnHeadings);
	}

	/**	Obtain a description of a node B-tree leaf node. This is typically used for debugging.
	*
	*	@return	A string describing the node B-tree leaf node.
	*/
	@Override
	public String toString()
	{
		return super.toString() + " Parent NID "+ nidParent;
	}
}