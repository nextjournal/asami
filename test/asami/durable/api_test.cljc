(ns asami.durable.api-test
  "Tests the public query functionality on the durable store"
  (:require [asami.core :refer [q show-plan create-database connect db transact delete-database
                                entity as-of since import-data export-data]]
            [asami.index :as i]
            [asami.multi-graph :as m]
            [asami.internal :refer [now]]
            [asami.durable.store :as durable]
            [asami.durable.graph]
            [schema.core :as s]
            #?(:clj  [clojure.test :refer [is use-fixtures testing]]
               :cljs [clojure.test :refer-macros [is run-tests use-fixtures testing]])
            #?(:clj  [schema.test :as st :refer [deftest]]
               :cljs [schema.test :as st :refer-macros [deftest]]))
  #?(:clj (:import [clojure.lang ExceptionInfo])))

(deftest test-create
  (let [c1 (create-database "asami:mem://papaya")
        c2 (create-database "asami:mem://papaya")
        c3 (create-database "asami:mem://cumquat")
        c4 (create-database "asami:mem://cumquat")
        c5 (create-database "asami:multi://cumquat")
        local-name "asami:local://cumquat"
        c6 (create-database local-name)]
    (is c1)
    (is (not c2))
    (is c3)
    (is (not c4))
    (is c5)
    (is c6)
    (is (thrown-with-msg? ExceptionInfo #"Unknown graph URI schema"
                          (create-database "asami:other://cumquat")))
    (is (durable/db-exists? "cumquat"))
    (delete-database local-name)
    (is (not (durable/db-exists? "cumquat")))))

(deftest test-connect
  (let [c (connect "asami:local://apple")]
    (is (instance? asami.durable.graph.BlockGraph @(:grapha c)))
    (is (= "apple" (:name c))))
  (delete-database "asami:local://apple"))

(deftest load-data
  (let [c (connect "asami:local://test1")
        r (transact c {:tx-data [{:db/ident "bobid"
                                  :person/name "Bob"
                                  :person/spouse "aliceid"}
                                 {:db/ident "aliceid"
                                  :person/name "Alice"
                                  :person/spouse "bobid"}]})]
    (= (->> (:tx-data @r)
            (filter #(= :db/ident (second %)))
            (map #(nth % 2))
            set)
       #{"bobid" "aliceid"}))

  (let [c (connect "asami:local://test2")
        r (transact c {:tx-data [[:db/add :mem/node-1 :property "value"]
                                 [:db/add :mem/node-2 :property "other"]]})]

    (is (= 2 (count (:tx-data @r)))))

  (let [c (connect "asami:local://test2")
        maksim {:db/id -1
                :name  "Maksim"
                :age   45
                :wife {:db/id -2}
                :aka   ["Maks Otto von Stirlitz", "Jack Ryan"]}
        anna   {:db/id -2
                :name  "Anna"
                :age   31
                :husband {:db/id -1}
                :aka   ["Anitzka"]}
        {:keys [tempids tx-data] :as r} @(transact c [maksim anna])
        one (tempids -1)
        two (tempids -2)]
    (is (= 19 (count tx-data)))
    (is (= #{[one :db/ident one]
             [one :tg/entity true]
             [one :name "Maksim"]
             [one :age 45]
             [one :wife two]}
           (->> tx-data
                (filter #(= one (first %)))
                (remove #(= :aka (second %)))
                (map (partial take 3))
                set)))
    (is (= #{[two :db/ident two]
             [two :tg/entity true]
             [two :name "Anna"]
             [two :age 31]
             [two :husband one]}
           (->> tx-data
                (filter #(= two (first %)))
                (remove #(= :aka (second %)))
                (map (partial take 3))
                set))))

  (let [c (connect "asami:local://test3")
        maksim {:db/ident "maksim"
                :name  "Maksim"
                :age   45
                :wife {:db/ident "anna"}
                :aka   ["Maks Otto von Stirlitz", "Jack Ryan"]}
        anna   {:db/ident "anna"
                :name  "Anna"
                :age   31
                :husband {:db/ident "maksim"}
                :aka   ["Anitzka"]}
        {:keys [tempids tx-data] :as r} @(transact c [maksim anna])
        one (tempids "maksim")
        two (tempids "anna")]
    (is (= 19 (count tx-data)))
    (is (= #{[one :db/ident "maksim"]
             [one :tg/entity true]
             [one :name "Maksim"]
             [one :age 45]
             [one :wife two]}
           (->> tx-data
                (filter #(= one (first %)))
                (remove #(= :aka (second %)))
                (map (partial take 3))
                set)))
    (is (= #{[two :db/ident "anna"]
             [two :tg/entity true]
             [two :name "Anna"]
             [two :age 31]
             [two :husband one]}
           (->> tx-data
                (filter #(= two (first %)))
                (remove #(= :aka (second %)))
                (map (partial take 3))
                set))))

  (let [c (connect "asami:local://test4")
        r (transact c {:tx-triples [[:mem/node-1 :property "value"]
                                    [:mem/node-2 :property "other"]]})]

    (is (= 2 (count (:tx-data @r)))))

  (delete-database "asami:local://test1")
  (delete-database "asami:local://test2")
  (delete-database "asami:local://test3")
  (delete-database "asami:local://test4"))

(deftest test-entity
  (let [c (connect "asami:local://test4")
        maksim {:db/id -1
                :db/ident :maksim
                :name  "Maksim"
                :age   45
                :aka   ["Maks Otto von Stirlitz", "Jack Ryan"]}
        anna   {:db/id -2
                :db/ident :anna
                :name  "Anna"
                :age   31
                :husband {:db/id -1}
                :aka   ["Anitzka"]}
        pete   {:db/id -3
                :db/ident "pete"
                :name  #{"Peter" "Pete" "Petrov"}
                :age   25
                :aka ["Peter the Great" #{"Petey" "Petie"}]
                }
        {:keys [tempids tx-data] :as r} @(transact c [maksim anna pete])
        one (tempids -1)
        two (tempids -2)
        three (tempids -3)
        d (db c)]
    (is (= {:name  "Maksim"
            :age   45
            :aka   ["Maks Otto von Stirlitz", "Jack Ryan"]}
           (entity d one)))
    (is (= {:name  "Maksim"
            :age   45
            :aka   ["Maks Otto von Stirlitz", "Jack Ryan"]}
           (entity d :maksim)))
    (is (= {:name  "Anna"
            :age   31
            :husband {:db/ident :maksim}
            :aka   ["Anitzka"]}
           (entity d :anna)))
    (is (= {:name  #{"Peter" "Pete" "Petrov"}
            :age   25
            :aka ["Peter the Great" ["Petey" "Petie"]]}
           (entity d three))))
  (delete-database "asami:local://test4"))

(deftest test-entity-arrays
  (let [c (connect "asami:local://test4")
        data {:db/id -1
              :db/ident :home
              :name  "Home"
              :address nil
              :rooms   ["Room 1" nil "Room 2" nil "Room 3"]}
        {:keys [tempids tx-data] :as r} @(transact c [data])
        one (tempids -1)
        d (db c)]
    (is (= 19 (count tx-data)))
    (is (= 2 (count (filter #(and (= :tg/first (nth % 1)) (= :tg/nil (nth % 2))) tx-data))))
    (is (= {:name  "Home"
            :address nil
            :rooms   ["Room 1" nil "Room 2" nil "Room 3"]}
           (entity d one))))
  (delete-database "asami:local://test4"))

(deftest test-entity-nested
  (let [c (connect "asami:local://test4b")
        d1 {:db/id -1
            :db/ident "nested-object"
            :name "nested"}
        d2 {:db/id -2
            :name "nested2"}
        data {:db/id -3
              :db/ident "top"
              :name  "Main"
              :sub   {:db/ident "nested-object"}}
        data2 {:db/id -4
              :db/ident "top2"
              :name  "Main2"
              :sub   {:db/id -2}}
        r0 @(transact c [d1])
        {:keys [tempids tx-data] :as r} @(transact c [d2 data data2])
        dx (tempids -2)
        one (tempids -3)
        two (tempids -4)
        d (db c)]
    (is (= {:name  "Main"
            :sub   {:db/ident "nested-object"}}
           (entity d one)))
    (is (= {:name  "Main"
            :sub   {:name "nested"}}
           (entity d one true)))
    (is (= {:name  "Main2"
            :sub   {:db/ident dx}}
           (entity d two)))
    (is (= {:name  "Main2"
            :sub   {:name "nested2"}}
           (entity d two true))))
  (delete-database "asami:local://test4b"))

(defn sleep [msec]
  #?(:clj
     (Thread/sleep msec)
     :cljs
     (let [deadline (+ msec (.getTime (js/Date.)))]
       (while (> deadline (.getTime (js/Date.)))))))

(deftest test-as
  (let [t0 (now)
        _ (sleep 100)
        c (connect "asami:local://test5")
        _ (sleep 100)
        t1 (now)
        maksim {:db/id -1
                :db/ident :maksim
                :name  "Maksim"
                :age   45
                :aka   ["Maks Otto von Stirlitz", "Jack Ryan"]}
        anna   {:db/id -2
                :db/ident :anna
                :name  "Anna"
                :age   31
                :husband {:db/ident :maksim}
                :aka   ["Anitzka"]}
        _ (sleep 100)
        _ @(transact c [maksim])
        _ (sleep 100)
        t2 (now)
        _ (sleep 100)
        _ @(transact c [anna])
        _ (sleep 100)
        t3 (now)
        latest-db (db c)
        db0 (as-of latest-db t0)  ;; before
        db1 (as-of latest-db t1)  ;; empty
        db2 (as-of latest-db t2)  ;; after first tx
        db3 (as-of latest-db t3)  ;; after second tx
        db0' (as-of latest-db 0)  ;; empty
        db1' (as-of latest-db 1)  ;; after first tx
        db2' (as-of latest-db 2)  ;; after second tx
        db3' (as-of latest-db 3)  ;; still after second tx

        db0* (since latest-db t0)  ;; before
        db1* (since latest-db t1)  ;; after first tx
        db2* (since latest-db t2)  ;; after second tx
        db3* (since latest-db t3)  ;; well after second tx
        db0*' (since latest-db 0)  ;; after first tx
        db1*' (since latest-db 1)  ;; after second tx 
        db2*' (since latest-db 2)  ;; still after second tx
        db3*' (since latest-db 3)  ;; still after second tx
        eq (fn [{{{s1 :root-id} :spot {p1 :root-id} :post {o1 :root-id} :ospt} :bgraph}
                {{{s2 :root-id} :spot {p2 :root-id} :post {o2 :root-id} :ospt} :bgraph}]
             (and (= s1 s2) (= p1 p2) (= o1 o2)))]
    
    (is (eq db0 db0'))
    (is (eq db0 db1))
    (is (eq db2 db1'))
    (is (eq db3 db2'))
    (is (eq db2' db3'))
    (is (eq db0 db0*))
    (is (eq db2 db1*))
    (is (eq db3 db2*))
    (is (= nil db3*))
    (is (eq db2 db0*'))
    (is (eq db3 db1*'))
    (is (= nil db2*'))
    (is (= nil db3*'))
    (is (eq db3 latest-db))
    (is (= (set (q '[:find ?name :where [?e :name ?name]] db0))
           #{}))
    (is (= (set (q '[:find ?name :where [?e :name ?name]] db1))
           #{}))
    (is (= (set (q '[:find ?name :where [?e :name ?name]] db2))
           #{["Maksim"]}))
    (is (= (set (q '[:find ?name :where [?e :name ?name]] db3))
           #{["Maksim"] ["Anna"]})))
  (delete-database "asami:local://test5"))

(deftest test-update
  (let [c (connect "asami:local://test6")
        maksim {:db/id -1
                :db/ident :maksim
                :name  "Maksim"
                :age   45
                :aka   ["Maks Otto von Stirlitz", "Jack Ryan"]}
        anna   {:db/id -2
                :db/ident :anna
                :name  "Anna"
                :age   31
                :husband {:db/id -1}
                :aka   ["Anitzka"]}
        {db1 :db-after} @(transact c [maksim anna])
        anne   {:db/ident :anna
                :name' "Anne"
                :age   32}
        {db2 :db-after} @(transact c [anne])
        maks   {:db/ident :maksim
                :aka' ["Maks Otto von Stirlitz"]}
        {db3 :db-after} @(transact c [maks])]
    (is (= (set (q '[:find ?aka :where [?e :name "Maksim"] [?e :aka ?a] [?a :tg/contains ?aka]] db1))
           #{["Maks Otto von Stirlitz"] ["Jack Ryan"]}))
    (is (= (set (q '[:find ?age :where [?e :db/ident :anna] [?e :age ?age]] db1))
           #{[31]}))
    (is (= (set (q '[:find ?name :where [?e :db/ident :anna] [?e :name ?name]] db1))
           #{["Anna"]}))
    (is (= (set (q '[:find ?age :where [?e :db/ident :anna] [?e :age ?age]] db2))
           #{[31] [32]}))
    (is (= (set (q '[:find ?name :where [?e :db/ident :anna] [?e :name ?name]] db2))
           #{["Anne"]}))
    (is (= (set (q '[:find ?aka :where [?e :name "Maksim"] [?e :aka ?a] [?a :tg/contains ?aka]] db3))
           #{["Maks Otto von Stirlitz"]})))
  (delete-database "asami:local://test6"))

(deftest test-append
  (let [c (connect "asami:local://test7")
        maksim {:db/id -1
                :db/ident :maksim
                :name  "Maksim"
                :age   45
                :aka   ["Maks Otto von Stirlitz", "Jack Ryan"]}
        anna   {:db/id -2
                :db/ident :anna
                :name  "Anna"
                :age   31
                :husband {:db/id -1}
                :aka   ["Anitzka" "Annie"]}
        {db1 :db-after :as tx1} @(transact c [maksim anna])
        anne   {:db/ident :anna
                :aka+ "Anne"
                :age   32
                :friend+ "Peter"}
        {db2 :db-after :as tx2} @(transact c [anne])]
    (is (= (set (q '[:find ?aka :where [?e :name "Anna"] [?e :aka ?a] [?a :tg/contains ?aka]] db1))
           #{["Anitzka"] ["Annie"]}))
    (is (= (set (q '[:find ?age :where [?e :db/ident :anna] [?e :age ?age]] db1))
           #{[31]}))
    (is (= {:name "Anna" :age 31 :aka ["Anitzka" "Annie"] :husband {:db/ident :maksim}}
           (entity db1 :anna)))

    (is (= (set (q '[:find ?aka :where [?e :name "Anna"] [?e :aka ?a] [?a :tg/contains ?aka]] db2))
           #{["Anitzka"] ["Annie"] ["Anne"]}))
    (is (= (set (q '[:find ?age :where [?e :db/ident :anna] [?e :age ?age]] db2))
           #{[31] [32]}))
    (is (= (set (q '[:find ?friend :where [?e :name "Anna"] [?e :friend ?f] [?f :tg/contains ?friend]] db2))
           #{["Peter"]}))
    (is (= {:name "Anna" :age #{31 32} :aka ["Anitzka" "Annie" "Anne"] :friend ["Peter"] :husband {:db/ident :maksim}}
           (entity db2 :anna))))
  (delete-database "asami:local://test7"))

(deftest test-filter-function
  (testing "filter function is constructed properly"
    (let [conn (connect "asami:local://test8")]
      (deref (transact conn {:tx-data [{:movie/title "Explorers"
                                        :movie/genre "adventure/comedy/family"
                                        :movie/release-year 1985}
                                       {:movie/title "Demolition Man"
                                        :movie/genre "action/sci-fi/thriller"
                                        :movie/release-year 1993}
                                       {:movie/title "Johnny Mnemonic"
                                        :movie/genre "cyber-punk/action"
                                        :movie/release-year 1995}
                                       {:movie/title "Toy Story"
                                        :movie/genre "animation/adventure"
                                        :movie/release-year 1995}]}))
      (is (= [["Explorers"]
              ["Toy Story"]]
             (q '{:find [?name]
                  :where [[?m :movie/title ?name]
                          [?m :movie/genre ?genre]
                          [(re-find #"comedy|animation" ?genre)]]}
                (db conn)))))
    (delete-database "asami:local://test8")))

(def transitive-data
    [{:db/id -1 :name "Washington Monument"}
     {:db/id -2 :name "National Mall"}
     {:db/id -3 :name "Washington, DC"}
     {:db/id -4 :name "USA"}
     {:db/id -5 :name "Earth"}
     {:db/id -6 :name "Solar System"}
     {:db/id -7 :name "Orion-Cygnus Arm"}
     {:db/id -8 :name "Milky Way Galaxy"}
     [:db/add -1 :is-in -2]
     [:db/add -2 :is-in -3]
     [:db/add -3 :is-in -4]
     [:db/add -4 :is-in -5]
     [:db/add -5 :is-in -6]
     [:db/add -6 :is-in -7]
     [:db/add -7 :is-in -8]])

#_(deftest test-transitive
  (let [c (connect "asami:local://test8")
        tx (transact c {:tx-data transitive-data})
        d (:db-after @tx)]
    (is (=
         (q '{:find [[?name ...]]
              :where [[?e :name "Washington Monument"]
                      [?e :is-in ?e2]
                      [?e2 :name ?name]]} d)
         ["National Mall"]))
    (is (=
         (q '{:find [[?name ...]]
              :where [[?e :name "Washington Monument"]
                      [?e :is-in* ?e2]
                      [?e2 :name ?name]]} d)
         ["Washington Monument" "National Mall" "Washington, DC" "USA" "Earth" "Solar System" "Orion-Cygnus Arm" "Milky Way Galaxy"]))
    (is (=
         (q '{:find [[?name ...]]
              :where [[?e :name "Washington Monument"]
                      [?e :is-in+ ?e2]
                      [?e2 :name ?name]]} d)
         ["National Mall" "Washington, DC" "USA" "Earth" "Solar System" "Orion-Cygnus Arm" "Milky Way Galaxy"])))
  (delete-database "asami:local://test8"))

;; tests both the show-plan function and the options
(deftest test-plan
  (let [c (connect "asami:local://test9")
        {d :db-after :as tx} @(transact c {:tx-data transitive-data})
        p1 (show-plan '[:find [?name ...]
                         :where [?e :name "Washington Monument"]
                         [?e :is-in ?e2]
                         [?e2 :name ?name]] d)
        p2 (show-plan '[:find [?name ...]
                         :where [?e2 :name ?name]
                         [?e :is-in ?e2]
                         [?e :name "Washington Monument"]] d)
        p3 (show-plan '[:find [?name ...]
                         :where [?e2 :name ?name]
                         [?e :is-in ?e2]
                         [?e :name "Washington Monument"]] d :planner :user)
        p4 (show-plan '[:find (count ?name)
                         :where [?e2 :name ?name]
                         [?e :is-in ?e2]
                         [?e :name "Washington Monument"]] d)]
    (is (= p1 '{:plan [[?e :name "Washington Monument"]
                       [?e :is-in ?e2]
                       [?e2 :name ?name]]}))
    (is (= p2 '{:plan [[?e :name "Washington Monument"]
                       [?e :is-in ?e2]
                       [?e2 :name ?name]]}))
    (is (= p3 '{:plan [[?e2 :name ?name]
                       [?e :is-in ?e2]
                       [?e :name "Washington Monument"]]})))
  (delete-database "asami:local://test9"))

(deftest test-plan-with-opt
  (let [c (connect "asami:local://test10")
        {d :db-after :as tx} @(transact c {:tx-data [{:movie/title "Explorers"
                                                      :movie/genre "adventure/comedy/family"
                                                      :movie/release-year 1985}
                                                     {:movie/title "Demolition Man"
                                                      :movie/genre "action/sci-fi/thriller"
                                                      :movie/release-year 1993}
                                                     {:movie/title "Johnny Mnemonic"
                                                      :movie/genre "cyber-punk/action"
                                                      :movie/release-year 1995}
                                                     {:movie/title "Toy Story"
                                                      :movie/genre "animation/adventure/comedy"
                                                      :movie/release-year 1995
                                                      :movie/sequel "Toy Story 2"}
                                                     {:movie/title "Sense and Sensibility"
                                                      :movie/genre "drama/romance"
                                                      :movie/release-year 1995}]})
        p1 (show-plan '[:find ?name ?sequel
                        :where [?m :movie/title ?name]
                        [?m :movie/release-year 1995]
                        (optional [?m :movie/sequel ?sequel])] d)]
    (is (= p1 '{:plan ([?m :movie/release-year 1995] (optional [?m :movie/sequel ?sequel]) [?m :movie/title ?name])})))
  (delete-database "asami:local://test10"))

(def opt-data
  [{:db/ident "austen"
    :name "Austen, Jane"}
   {:author {:db/ident "austen"}
    :title "Pride and Prejudice"}
   {:author {:db/ident "austen"}
    :label "Emma"}
   {:author {:db/ident "austen"}
    :label "Sense and Sensibility"}
   {:db/ident "bronte"
    :name "Brontë, Charlotte"}
   {:author {:db/ident "bronte"}
    :title "Jane Eyre"}
   {:db/ident "shelley"
    :name "Shelley, Mary"}])

(deftest test-optional
  (let [c (connect "asami:local://test10")
        {d :db-after :as tx} @(transact c {:tx-data opt-data})
        rx (q '[:find ?name ?t
                :where [?a :name ?name]
                (and [?book :author ?a] [?book :title ?t])] d)
        ry (q '[:find ?name ?t
                :where [?a :name ?name]
                (and [?book :author ?a] (or [?book :title ?t] [?book :label ?t]))] d)
        r1 (q '[:find ?name ?t
                :where [?a :name ?name]
                (optional [?book :author ?a] [?book :title ?t])] d)
        r2 (q '[:find ?name ?t
                :where [?a :name ?name]
                (optional [?book :author ?a] (or [?book :title ?t] [?book :label ?t]))] d)]
    (is (= #{["Austen, Jane" "Pride and Prejudice"]
             ["Brontë, Charlotte" "Jane Eyre"]}
           (set rx)))
    (is (= #{["Austen, Jane" "Pride and Prejudice"]
             ["Austen, Jane" "Sense and Sensibility"]
             ["Austen, Jane" "Emma"]
             ["Brontë, Charlotte" "Jane Eyre"]}
           (set ry)))
    (is (= #{["Austen, Jane" "Pride and Prejudice"]
             ["Brontë, Charlotte" "Jane Eyre"]
             ["Shelley, Mary" nil]}
           (set r1)))
    (is (= #{["Austen, Jane" "Pride and Prejudice"]
             ["Austen, Jane" "Sense and Sensibility"]
             ["Austen, Jane" "Emma"]
             ["Brontë, Charlotte" "Jane Eyre"]
             ["Shelley, Mary" nil]}
           (set r2))))
  (delete-database "asami:local://test10"))

(deftest test-explicit-default-graph
  (testing "explicity default graph"
    (let [conn (connect "asami:local://test11")]
      (deref (transact conn {:tx-data [{:movie/title "Explorers"
                                        :movie/genre "adventure/comedy/family"
                                        :movie/release-year 1985}]}))
      (is (= "Explorers"
             (q '{:find [?name .]
                  :in [$]
                  :where [[?m :movie/title ?name]
                          [?m :movie/release-year 1985]]}
                (db conn)))))
    (delete-database "asami:local://test11")))

(def raw-data
  [[:tg/node-10511 :db/ident "charles"]
   [:tg/node-10511 :tg/entity true]
   [:tg/node-10511 :name "Charles"]
   [:tg/node-10511 :home :tg/node-10512]
   [:tg/node-10512 :db/ident "scarborough"]
   [:tg/node-10512 :town "Scarborough"]
   [:tg/node-10512 :county "Yorkshire"]
   [:tg/node-10513 :db/ident "jane"]
   [:tg/node-10513 :tg/entity true]
   [:tg/node-10513 :name "Jane"]
   [:tg/node-10513 :home :tg/node-10512]])

(deftest test-import-export
  (testing "Loading raw data"
    (let [conn (connect "asami:local://test12")
          {d :db-after} @(import-data conn raw-data)]
      (is (= #{[:tg/node-10511 "Charles"]
               [:tg/node-10513 "Jane"]}
             (set (q '[:find ?e ?n :where [?e :name ?n]] d))))
      (is (= (set raw-data)
             (set (export-data (db conn))))))
    (let [conn (connect "asami:local://test13")
          {d :db-after} @(import-data conn (str raw-data))]
      (is (= #{[:tg/node-10511 "Charles"]
               [:tg/node-10513 "Jane"]}
             (set (q '[:find ?e ?n :where [?e :name ?n]] d))))
      (is (= (set raw-data)
             (set (export-data (db conn))))))
    (delete-database "asami:local://test12")
    (delete-database "asami:local://test13")))

#_(:cljs (run-tests))
