(ns ^{:doc "Storage protocols"
      :author "Paula Gearon"}
    asami.storage
    (:require #?(:clj  [schema.core :as s]
                 :cljs [schema.core :as s :include-macros true])))

(defprotocol Connection
  (get-name [this] "Retrieves the name of the database")
  (next-tx [this] "Returns the next transaction ID that this connection will use")
  (db [this] "Retrieves the latest database from this connection")
  (delete-database [this] "Removes all resources for a given connection")
  (release [this] "Releases the resources associated with this connection")
  (transact-update [this update-fn] "Updates a graph in the database with the provided function.
                                     Function args are connection and transaction-id")
  (transact-data [this asserts retracts] "Updates the database with provided data"))

(defprotocol Database
  (as-of [this t] "Retrieves a database as of a given moment, inclusive")
  (as-of-t [this] "Returns the t point for a database")
  (since [this t] "Retrieves a database since a given moment, exclusive")
  (since-t [this] "Returns the since point for a database")
  (graph [this] "Returns the internal graph for the database")
  (entity [this id] [this id nested?] "Returns an entity for an identifier"))

(def DatabaseType (s/pred #(satisfies? Database %)))
(def ConnectionType (s/pred #(satisfies? Connection %)))

