/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.tdb.store.nodetable;

import org.apache.jena.graph.Node;
import org.apache.jena.shared.OperationDeniedException;
import org.apache.jena.tdb.base.objectfile.ObjectFile;
import org.apache.jena.tdb.base.record.Record;
import org.apache.jena.tdb.base.record.RecordFactory;
import org.apache.jena.tdb.index.Index;
import org.apache.jena.tdb.lib.NodeLib;
import org.apache.jena.tdb.store.NodeId;

import java.util.Iterator;

// TODO: Is method isVariable() for the numeric type?
// Contains Index of each Node type (numeric, blank, literal and URI/IRI).
public class NodeTableNativeSplitter implements Index
{
  private Index nodeHashToIdUri, nodeHashToIdBlank, nodeHashToIdLiteral, nodeHashToIdNumeric;
  private ObjectFile objects;

  // Builder method. Separates index of notes into their representation.
  public static NodeTableNativeSplitter buildFromIndex(Index nodes, ObjectFile objects)
  {
    NodeTableNativeSplitter splitter = new NodeTableNativeSplitter();
    splitter.objects = objects;
    distributeRecords(nodes, splitter);

    return splitter;
  }

  // Iterates index of records and separates them into each index Node representation.
  private static void distributeRecords(Index main, NodeTableNativeSplitter splitter)
  {
    Iterator<Record> it = main.iterator();

    while (it.hasNext())
    {
      splitter.add(it.next());
    }
  }

  // Returns RecordFactory based on node representation.
  public RecordFactory getRecordFactory(Node node)
  {
    if (node.isBlank())
      return this.nodeHashToIdBlank.getRecordFactory();

    else if (node.isLiteral())
      return this.nodeHashToIdLiteral.getRecordFactory();

    else if (node.isURI())
      return this.nodeHashToIdUri.getRecordFactory();

    else if (node.isVariable())
      return this.nodeHashToIdNumeric.getRecordFactory();

    return null;
  }

  // Converts Record instance into NodeId instance.
  // Not sure, but it seems like records are always inserted at index 0.
  private NodeId record2Id(Record record)
  {
    return NodeId.create(record.getValue());
  }

  // Converts NodeId instance into Node instance.
  private synchronized Node id2Node(NodeId id)
  {
    if (NodeId.isDoesNotExist(id) || this.objects.length() <= id.getId())
      return null;

    else if (NodeId.isAny(id))
      return Node.ANY;

    return NodeLib.fetchDecode(id.getId(), this.objects);
  }

  // Converts Record instance into Node instance.
  private Node record2Node(Record record)
  {
    return id2Node(record2Id(record));
  }

  @Override
  public Record find(Record record)
  {
    Node n = record2Node(record);

    if (n.isVariable())
      return this.nodeHashToIdNumeric.find(record);

    else if (n.isURI())
      return this.nodeHashToIdUri.find(record);

    else if (n.isLiteral())
      return this.nodeHashToIdLiteral.find(record);

    else
      return this.nodeHashToIdBlank.find(record);
  }

  @Override
  public boolean contains(Record record)
  {
    Node n = record2Node(record);

    if (n.isBlank())
      return this.nodeHashToIdBlank.contains(record);

    else if (n.isVariable())
      return this.nodeHashToIdNumeric.contains(record);

    else if (n.isLiteral())
      return this.nodeHashToIdLiteral.contains(record);

    return this.nodeHashToIdUri.contains(record);
  }

  @Override
  public boolean add(Record record)
  {
    Node n = record2Node(record);

    if (n.isVariable())
      return this.nodeHashToIdNumeric.add(record);

    else if (n.isURI())
      return this.nodeHashToIdUri.add(record);

    else if (n.isLiteral())
      return this.nodeHashToIdLiteral.add(record);

    else
      return this.nodeHashToIdBlank.add(record);
  }

  @Override
  public boolean delete(Record record)
  {
    Node n = record2Node(record);

    if (n.isVariable())
      return this.nodeHashToIdNumeric.delete(record);

    else if (n.isURI())
      return this.nodeHashToIdUri.delete(record);

    else if (n.isLiteral())
      return this.nodeHashToIdLiteral.delete(record);

    else
      return this.nodeHashToIdBlank.delete(record);
  }

  public Iterator<Record> iterator(Node node)
  {
    if (node.isLiteral())
      return this.nodeHashToIdLiteral.iterator();

    else if (node.isURI())
      return this.nodeHashToIdUri.iterator();

    else if (node.isVariable())
      return this.nodeHashToIdNumeric.iterator();

    else if (node.isBlank())
      return this.nodeHashToIdBlank.iterator();

    return null;
  }

  public Iterator<Record> iterator(Record record)
  {
    return iterator(record2Node(record));
  }

  @Override
  public Iterator<Record> iterator()
  {
    throw new OperationDeniedException("Must be given node or record");
  }

  @Override
  public RecordFactory getRecordFactory()
  {
    throw new OperationDeniedException("Must be given node");
  }

  @Override
  public void close()
  {
    this.nodeHashToIdBlank.close();
    this.nodeHashToIdLiteral.close();
    this.nodeHashToIdUri.close();
    this.nodeHashToIdNumeric.close();
  }

  @Override
  public boolean isEmpty()
  {
    return this.nodeHashToIdBlank.isEmpty() && this.nodeHashToIdNumeric.isEmpty()
            && this.nodeHashToIdUri.isEmpty() && this.nodeHashToIdLiteral.isEmpty();
  }

  @Override
  public void clear()
  {
    this.nodeHashToIdLiteral.clear();
    this.nodeHashToIdUri.clear();
    this.nodeHashToIdNumeric.clear();
    this.nodeHashToIdBlank.clear();
  }

  @Override
  public void check()
  {
    this.nodeHashToIdBlank.check();
    this.nodeHashToIdNumeric.check();
    this.nodeHashToIdUri.check();
    this.nodeHashToIdLiteral.check();
  }

  @Override
  public long size()
  {
    if (this.nodeHashToIdUri.size() == -1 || this.nodeHashToIdBlank.size() == -1
            || this.nodeHashToIdNumeric.size() == -1 || this.nodeHashToIdLiteral.size() == -1)
      return -1;

    return this.nodeHashToIdUri.size() + this.nodeHashToIdBlank.size() +
            this.nodeHashToIdNumeric.size() + this.nodeHashToIdLiteral.size();
  }

  @Override
  public void sync()
  {
    this.nodeHashToIdLiteral.sync();
    this.nodeHashToIdUri.sync();
    this.nodeHashToIdNumeric.sync();
    this.nodeHashToIdBlank.sync();
  }
}
