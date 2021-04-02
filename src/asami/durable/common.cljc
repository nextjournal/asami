(ns ^{:doc "A common namespace for protocols and constants that are referenced
           from multiple files and/or between Clojure and ClojureScript files."
      :author "Paula Gearon"}
    asami.durable.common)

(def ^:const long-size "Number of bytes in a Long value"
  #?(:clj Long/BYTES :cljs 8 #_BigInt64Array.BYTES_PER_ELEMENT))

(def ^:const int-size "Number of bytes in a Integer value"
  #?(:clj Integer/BYTES :cljs 4 #_Int32Array.BYTES_PER_ELEMENT))

(def ^:const short-size "Number of bytes in a Short value"
  #?(:clj Short/BYTES :cljs 2 #_Int16Array.BYTES_PER_ELEMENT))

(def ^:const max-long "Maximum value that can be safely represented as a long"
  #?(:clj Long/MAX_VALUE :cljs js/Number.MAX_SAFE_INTEGER))

(defprotocol Forceable
  (force! [this] "Ensures that all written data is fully persisted"))

(defprotocol Closeable
  (close [this] "Closes and invalidates all associated resources")
  (delete! [this] "Remove any persistent resources"))

(defprotocol Transaction
  (rewind! [this] "Revert to the last commit point. Any blocks allocated since the last commit will be invalid.")
  (commit! [this] "Commits all blocks allocated since the last commit. These blocks are now read-only."))

(defprotocol TxData
  (get-tx-data [this] "Returns the data for a transaction in a vector of long values"))

(defprotocol TxStore
  (append-tx! [this tx] "Writes a transaction record. The record is a seq of longs")
  (get-tx [this id] "Retrieves a transaction record by ID")
  (latest [this] "Retrieves the last transaction record")
  (tx-count [this] "Retrieves the count of transaction records")
  (find-tx [this timestamp] "Finds the transaction number for a timestamp"))

(defprotocol DataStorage
  (find-object [pool id] "Retrieves an object by ID")
  (find-id [pool object] "Retrieves an ID for an object")
  (write! [pool object] "Retrieves an ID for an object, writing it if necessary. Returns a pair of the ID and the next version of the store. Idempotent.")
  (at [pool t] "Retrieve the data at a particular transaction."))

(defprotocol Paged
  (refresh! [this] "Refreshes the buffers")
  (read-byte [this offset] "Returns a byte from underlying pages")
  (read-short [this offset] "Returns a short from underlying pages. Offset in bytes.")
  (read-long [this offset] "Returns a long from underlying pages. Offset in bytes. Unlike other data types, these may not straddle boundaries")
  (read-bytes [this offset length] "Reads length bytes and returns as an array.")
  (read-bytes-into [this offset bytes] "Fills a byte array with data from the paged object"))

(defprotocol FlatStore
  (write-object! [this obj] "Writes an object to storage. Returns an ID")
  (get-object [this id] "Reads and object from storage, based on an ID"))

(defprotocol FlatRecords
  (append! [this v] "Writes a vector of long values. Returns an ID")
  (get-record [this id] "Reads a record of long values from storage, based on an ID")
  (next-id [this] "Returns the next ID that this store will return"))

(defprotocol TupleStorage
  (tuples-at [this root] "Returns this tuples index at a different root")
  (write-new-tx-tuple! [this tuple] "Adds a new tuple to the index in the current TX")
  (write-tuple! [this tuple] "Adds a tuple to the index")
  (delete-tuple! [this tuple] "Removes a tuple from the index. Returns both the index and the final element of the tuple")
  (find-tuples [this tuple] "Finds a tuples seq, returning a co-ordinate")
  (count-tuples [this tuple] "Finds and counts the size of a tuples seq"))
