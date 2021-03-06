package io.github.jmcleodfoss.pst;

/**	The MessageObjectWithBody class represents message objects which are expected to have a Body (and possibly a BodyHTML) tag. */
public class MessageObjectWithBody extends MessageObject {

	/**	Create a message object for a type with a Body tag for the given row in the folder contents table.
	*
	*	@param	contentsTable	The containing folder's contents table
	*	@param	messageRow	The row of the contents table from which to create the message
	*	@param	nbt		The PST file's node B-Tree
	*	@param	pstFile		The PST file's header, input stream, etc.
	*
	*	@throws	NotHeapNodeException	A heap which was not a node heap was found while building the message object / body.
	*	@throws UnknownClientSignatureException	An unknown client signature was encountered while building the message object / body.
	*	@throws UnparseablePropertyContextException	A bad / corrupt property context block was found while building the message object / body.
	*	@throws UnparseableTableContextException	A bad / corrupt table context block was found while building the message object / body.
	*	@throws	java.io.IOException	An I/O error was encounted while reading in the data for the message object / body.
	*/
	MessageObjectWithBody(final TableContext contentsTable, final int messageRow, final NodeMap nbt, final PSTFile pstFile)
	throws
		NotHeapNodeException,
		UnknownClientSignatureException,
		UnparseablePropertyContextException,
		UnparseableTableContextException,
		java.io.IOException
	{
		super(contentsTable, messageRow, nbt, pstFile);
	}

	/**	Extract the message body from the message object property context.
	*
	*	@param	pc	The message object property context, as retrieved by getMessage.
	*
	*	@return	The message body, as a string.
	*
	*	@see	#bodyHtml
	*	@see	#getMessage
	*	@see	Message#transportHeaders
	*/
	public String body(final PropertyContext pc)
	{
		return (String)pc.get(fUnicode ? PropertyTag.BodyW : PropertyTag.Body);
	}

	/**	Extract the HTML message body from the message object property context.
	*
	*	@param	pc	The message object property context, as retrieved by getMessage.
	*
	*	@return	The message body in HTML, if present, as a string.
	*
	*	@see	#body
	*	@see	#getMessage
	*	@see	Message#transportHeaders
	*/
	public String bodyHtml(final PropertyContext pc)
	{
		if (fUnicode) {
			final byte[] htmlData = (byte[])pc.get(PropertyTag.BodyHtmlW);
			return htmlData == null ? null : new String(htmlData);
		}

		return (String)pc.get(PropertyTag.BodyHtml);
	}
}
