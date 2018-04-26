(ns clauth.store.cassandra
  (:require [clauth.store :refer [Store]]
            [qbits.alia :as alia]
            [cheshire.core :refer :all]))

;; CASSANDRA AS A BACKING STORE
;; clauth always stores maps, and keys them off one of the values inside.
;; (:u, {:u "the_user" :p "the_password"} => ("the_user", {:u "the_user" :p "the_password"}))
;; we're going to hold the map as JSON internally so we can be flexible on value types

;; bind a session here to use alia.
(def ^:dynamic *session* nil)

(defn get-token-keyspace []
  (or (get (System/getenv) "CLAUTH_KEYSPACE") "clauth"))

;; default connection to env-configured cassandra host
(def conn0
  (alia/cluster
    {
      :contact-points [(or (get (System/getenv) "CASSANDRA_HOST") "localhost")]
      :port (int (or (get (System/getenv) "CASSANDRA_PORT") 9042))
    }))

;; in the bound *session* use the token keyspace, create if needed
(defn use-token-keyspace []
  (let [token-keyspace (get-token-keyspace)
        replication-strategy (or (get (System/getenv) "CASSANDRA_REPLICATION_STRATEGY"
                                  "SimpleStrategy"))
        replication-factor (int (or (get (System/getenv) "CASSANDRA_REPLICATION_FACTOR") 
                                  1))]
    (alia/execute *session* (format "CREATE KEYSPACE IF NOT EXISTS %s
                    WITH REPLICATION = {'class' : '%s', 
                                        'replication_factor' : %d};" 
                    token-keyspace
                    replication-strategy
                    replication-factor))
    (alia/execute *session* (format "USE %s"
                    token-keyspace))))


;; bind a session with the token keyspace using conn (as defined in scope)
(defmacro wcass
  [& body]
  `(binding [*session* (alia/connect ~'conn)]
    (use-token-keyspace)
    ~@body))

(defn ensure-namespace [namespace]
  (alia/execute *session* (format "CREATE TABLE IF NOT EXISTS %s
                                  (k text PRIMARY KEY, v text)"
                                  namespace)))

(defn namespaced-keys
  "get namespaced list of keys"
  [namespace conn]
  (map (fn [x] (:k x))
    (or (wcass 
      (do
        (ensure-namespace namespace)
        (alia/execute *session* (format "SELECT k FROM %s" namespace))))
      [])))

(defn all-in-namespace
  "get all items in namespace"
  [namespace conn]
  (let [ks (remove nil? (namespaced-keys namespace conn))]
    (if (not-empty ks)
      (wcass (map (fn [x] (parse-string (:v x) true))
              (alia/execute *session* (format "SELECT v FROM %s WHERE k in (%s)"
                                              namespace (clojure.string/join ","
                                                        (map (fn [x] (str "'" x "'"))
                                                          ks))))))
      [])))

(defn get-token-map [namespace conn k]
  (wcass
    (do
      (ensure-namespace namespace)
      (if-let [token-record (first (alia/execute *session* 
                              (format "SELECT v FROM %s WHERE k = '%s'"
                                        namespace k)))]
        (parse-string (:v token-record) true)
        nil))))

(defn drop-token [namespace conn k]
  (wcass
    (do
      (ensure-namespace namespace)
      (alia/execute *session* (format "DELETE FROM %s WHERE k = '%s'"
                                      namespace k)))))

(defn store-token [namespace conn key_param item]
  (wcass
    (do
      (ensure-namespace namespace)
      (let [item-string (generate-string item)]
        (alia/execute *session* (format "INSERT INTO %s (k, v) 
                                          VALUES ('%s', '%s')"
                                      namespace (key_param item) item-string))))))


(defrecord CassandraStore [namespace conn]
  Store
  (fetch [this t] (get-token-map namespace conn t))
  (revoke! [this t] (drop-token namespace conn t))
  (store! [this key_param item] (store-token namespace conn key_param item)
    item)
  (entries [this] (all-in-namespace namespace conn))
  (reset-store! [this] (wcass (alia/execute *session* (format "DROP KEYSPACE IF EXISTS %s" (get-token-keyspace))))))

(defn create-cassandra-store
  "Create a cassandra store"
  ([namespace]
  (CassandraStore. namespace conn0))
  ([namespace conn]
  (CassandraStore. namespace conn)))
