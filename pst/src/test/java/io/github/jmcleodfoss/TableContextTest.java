package io.github.jmcleodfoss.pst;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.lang.reflect.InvocationTargetException;
import junit.extensions.PrivilegedAccessor;
import org.junit.Test;

/** TBD */
public class TableContextTest extends TestFrame {
	@Test public void pst()
	throws
		BufferUnderflowException,
		FileNotFoundException,
		IllegalAccessException,
		InsufficientMemoryException,
		InstantiationException,
		IOException,
		NotPSTFileException,
		NoSuchMethodException,
		Throwable
	{
		test(ExtensionFileFilter.pstFileFilter);
	}

	protected void test(File file)
	throws
		BufferUnderflowException,
		FileNotFoundException,
		IllegalAccessException,
		InsufficientMemoryException,
		InstantiationException,
		IOException,
		NotPSTFileException,
		NoSuchMethodException,
		Throwable
	{
		try {
			PSTFile pstFile = new PSTFile(new java.io.FileInputStream(file));
			BlockBTree bbt = new BlockBTree(0, pstFile.header.bbtRoot, pstFile);
			NodeBTree nbt = new NodeBTree(0, pstFile.header.nbtRoot, pstFile);

			java.util.Iterator iterator = nbt.iterator();
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
						if (hon.clientSignature().equals(ClientSignature.TableContext))
							new TableContext(nodeDescr, hon, bbt, pstFile);
					} catch (NotHeapNodeException e) {
						// This is expected; we have no way to find out whether a node contains a heap-on-node until we start reading it.
						continue;
					} catch (UnknownClientSignatureException e) {
//					} catch (UnparseableTableContextException e) {
					}
				}
			}
		} catch (IOException e) {
			if (e.toString().equals("java.io.IOException: Cannot allocate memory")) {
				throw new InsufficientMemoryException(e);
			}
			throw e;
		}
	}
}
