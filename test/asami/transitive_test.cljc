(ns asami.transitive-test
  "Tests internals of the query portion of the memory storage"
  (:require
            [asami.graph :refer [Graph graph-add resolve-pattern]]
            [asami.index :refer [empty-graph]]
            [asami.multi-graph :refer [empty-multi-graph]]
            #?(:clj  [schema.core :as s]
               :cljs [schema.core :as s :include-macros true])
            #?(:clj  [clojure.test :refer [is use-fixtures testing]]
               :cljs [clojure.test :refer-macros [is run-tests use-fixtures testing]])
            #?(:clj  [schema.test :as st :refer [deftest]]
               :cljs [schema.test :as st :refer-macros [deftest]]))
  #?(:clj (:import [clojure.lang ExceptionInfo])))

(use-fixtures :once st/validate-schemas)

(def default-tx 1)

(defn assert-data [g d]
  (reduce (fn [g [s p o]] (graph-add g s p o default-tx)) g d))

(defn unordered-resolve
  [g pattern]
  (into #{} (resolve-pattern g pattern)))

(defn t [x] (with-meta x {:trans :plus}))

(defn simple [g]
  (let [r1 (unordered-resolve g '[:a :p1+ ?x])
        r1' (unordered-resolve g '[:a :p1* ?x])
        r2 (unordered-resolve g '[?x :p1+ :c])
        r2' (unordered-resolve g '[?x :p1* :c])
        r3 (unordered-resolve g '[:a ?p+ ?x])
        r3' (unordered-resolve g '[:a ?p* ?x])
        r4 (unordered-resolve g '[?x ?p+ :c])
        r4' (unordered-resolve g '[?x ?p* :c])
        r5 (unordered-resolve g [:a (t '?p) '?x])  ;; duplicate of above, with alternate syntax
        r6 (unordered-resolve g ['?x (t '?p) :c])]
    (is (= #{[:b] [:c]} r1))
    (is (= #{[:a] [:b] [:c]} r1'))
    (is (= #{[:b] [:a]} r2))
    (is (= #{[:c] [:b] [:a]} r2'))
    (is (= #{[:p1 :b] [:p1 :c]} r3))
    (is (= #{[nil :a] [:p1 :b] [:p1 :c]} r3'))
    (is (= #{[:b :p1] [:a :p1]} r4))
    (is (= #{[:c nil] [:b :p1] [:a :p1]} r4'))
    (is (= #{[:p1 :b] [:p1 :c]} r5))
    (is (= #{[:b :p1] [:a :p1]} r6))))

(deftest test-simple
  (let [g (assert-data empty-graph [[:a :p1 :b] [:b :p1 :c]])
        gm (assert-data empty-multi-graph [[:a :p1 :b] [:b :p1 :c]])]
    (simple g)
    (simple gm)))

(defn simple-path [g]
  (let [r1 (resolve-pattern g '[:a ?p* :c])]
    (is (= [[[:p1 :p1]]] r1))))

(deftest test-simple-path
  (let [g (assert-data empty-graph [[:a :p1 :b] [:b :p1 :c]])
        gm (assert-data empty-multi-graph [[:a :p1 :b] [:b :p1 :c]])]
    (simple-path g)
    (simple-path gm)))

(def simple-branch-data
  [[:a :p1 :b]
   [:b :p1 :c]
   [:b :p1 :d]
   [:c :p1 :e]
   [:d :p1 :f]])

(defn branch [g]
  (let [r1 (unordered-resolve g '[:a :p1+ ?x])
        r1' (unordered-resolve g '[:a :p1* ?x])
        r2 (unordered-resolve g '[?x :p1+ :e])
        r2' (unordered-resolve g '[?x :p1* :e])
        r3 (unordered-resolve g '[:a ?p+ ?x])
        r3' (unordered-resolve g '[:a ?p* ?x])
        r4 (unordered-resolve g '[?x ?p+ :e])
        r4' (unordered-resolve g '[?x ?p* :e])
        r5 (unordered-resolve g '[:b :p1+ ?x])
        r5' (unordered-resolve g '[:b :p1* ?x])
        r6 (unordered-resolve g '[?x :p1+ :c])
        r6' (unordered-resolve g '[?x :p1* :c])
        r7 (unordered-resolve g '[:b ?p+ ?x])
        r7' (unordered-resolve g '[:b ?p* ?x])
        r8 (unordered-resolve g '[?x ?p+ :c])
        r8' (unordered-resolve g '[?x ?p* :c])]
    (is (= #{[:b] [:c] [:d] [:e] [:f]} r1))
    (is (= #{[:a] [:b] [:c] [:d] [:e] [:f]} r1'))
    (is (= #{[:c] [:b] [:a]} r2))
    (is (= #{[:e] [:c] [:b] [:a]} r2'))
    (is (= #{[:p1 :b] [:p1 :c] [:p1 :d] [:p1 :e] [:p1 :f]} r3))
    (is (= #{[nil :a] [:p1 :b] [:p1 :c] [:p1 :d] [:p1 :e] [:p1 :f]} r3'))
    (is (= #{[:c :p1] [:b :p1] [:a :p1]} r4))
    (is (= #{[:e nil] [:c :p1] [:b :p1] [:a :p1]} r4'))
    (is (= #{[:c] [:d] [:e] [:f]} r5))
    (is (= #{[:b] [:c] [:d] [:e] [:f]} r5'))
    (is (= #{[:b] [:a]} r6))
    (is (= #{[:c] [:b] [:a]} r6'))
    (is (= #{[:p1 :c] [:p1 :d] [:p1 :e] [:p1 :f]} r7))
    (is (= #{[nil :b] [:p1 :c] [:p1 :d] [:p1 :e] [:p1 :f]} r7'))
    (is (= #{[:b :p1] [:a :p1]} r8))
    (is (= #{[:c nil] [:b :p1] [:a :p1]} r8'))))

(deftest test-branch
  (let [g (assert-data empty-graph simple-branch-data)
        gm (assert-data empty-multi-graph simple-branch-data)]
    (branch g)
    (branch gm)))

(defn branch-path [g]
  (let [r1 (resolve-pattern g '[:a ?p* :c])
        r2 (resolve-pattern g '[:a ?p* :e])]
    (is (= [[[:p1 :p1]]] r1))
    (is (= [[[:p1 :p1 :p1]]] r2))))

(deftest test-branch-path
  (let [g (assert-data empty-graph simple-branch-data)
        gm (assert-data empty-multi-graph simple-branch-data)]
    (branch-path g)
    (branch-path gm)))

(def dbl-branch-data
  [[:a :p1 :b]
   [:b :p1 :c]
   [:b :p2 :c]
   [:b :p1 :d]
   [:c :p2 :e]
   [:d :p2 :f]
   [:g :p2 :h]])

(defn dbl-branch [g]
  (let [r1 (unordered-resolve g '[:a :p1+ ?x])
        r1' (unordered-resolve g '[:a :p1* ?x])
        r2 (unordered-resolve g '[?x :p2+ :e])
        r2' (unordered-resolve g '[?x :p2* :e])
        r3 (unordered-resolve g '[:a ?p+ ?x])
        r3' (unordered-resolve g '[:a ?p* ?x])
        r4 (unordered-resolve g '[?x ?p+ :e])
        r4' (unordered-resolve g '[?x ?p* :e])
        r5 (unordered-resolve g '[:b :p1+ ?x])
        r5' (unordered-resolve g '[:b :p1* ?x])
        r6 (unordered-resolve g '[?x :p1+ :c])
        r6' (unordered-resolve g '[?x :p1* :c])
        r7 (unordered-resolve g '[:b ?p+ ?x])
        r7' (unordered-resolve g '[:b ?p* ?x])
        r8 (unordered-resolve g '[?x ?p+ :c])
        r8' (unordered-resolve g '[?x ?p* :c])]
    (is (= #{[:b] [:c] [:d]} r1))
    (is (= #{[:a] [:b] [:c] [:d]} r1'))
    (is (= #{[:c] [:b]} r2))
    (is (= #{[:e] [:c] [:b]} r2'))
    (is (= #{[:p1 :b] [:p1 :c] [:p1 :d] [:p1 :e] [:p1 :f]} r3))
    (is (= #{[nil :a] [:p1 :b] [:p1 :c] [:p1 :d] [:p1 :e] [:p1 :f]} r3'))
    (is (= #{[:c :p2] [:b :p2] [:a :p2]} r4))
    (is (= #{[:e nil] [:c :p2] [:b :p2] [:a :p2]} r4'))
    (is (= #{[:c] [:d]} r5))
    (is (= #{[:b] [:c] [:d]} r5'))
    (is (= #{[:b] [:a]} r6))
    (is (= #{[:c] [:b] [:a]} r6'))
    (is (= #{[:p1 :c] [:p2 :c] [:p1 :d] [:p1 :e] [:p2 :e] [:p1 :f]} r7))
    (is (= #{[nil :b] [:p1 :c] [:p2 :c] [:p1 :d] [:p1 :e] [:p2 :e] [:p1 :f]} r7'))
    (is (= #{[:b :p1] [:b :p2] [:a :p1] [:a :p2]} r8))
    (is (= #{[:c nil] [:b :p1] [:b :p2] [:a :p1] [:a :p2]} r8'))))

(deftest test-dbl-branch
  (let [g (assert-data empty-graph dbl-branch-data)
        gm (assert-data empty-multi-graph dbl-branch-data)]
    (dbl-branch g)
    (dbl-branch gm)))

(defn dbl-branch-path [g]
  (let [r1 (resolve-pattern g '[:a ?p* :c])
        r2 (resolve-pattern g '[:a ?p* :f])
        r3 (resolve-pattern g '[:a ?p* :h])]
    (is (= [[[:p1 :p1]]] r1))
    (is (= [[[:p1 :p1 :p2]]] r2))
    (is (= [] r3))))

(deftest test-dbl-branch-path
  (let [g (assert-data empty-graph dbl-branch-data)
        gm (assert-data empty-multi-graph dbl-branch-data)]
    (dbl-branch-path g)
    (dbl-branch-path gm)))

(def simple-loop-data
  [[:a :p1 :b]
   [:b :p1 :c]
   [:c :p1 :a]])

(defn data-loop [g]
  (let [r1 (unordered-resolve g '[:a :p1+ ?x])
        r1' (unordered-resolve g '[:a :p1* ?x])
        r2 (unordered-resolve g '[?x :p1+ :a])
        r2' (unordered-resolve g '[?x :p1* :a])
        r3 (unordered-resolve g '[:a ?p+ ?x])
        r3' (unordered-resolve g '[:a ?p* ?x])
        r4 (unordered-resolve g '[?x ?p+ :a])
        r4' (unordered-resolve g '[?x ?p* :a])]
    (is (= #{[:b] [:c] [:a]} r1))
    (is (= #{[:b] [:c] [:a]} r1'))
    (is (= #{[:c] [:b] [:a]} r2))
    (is (= #{[:c] [:b] [:a]} r2'))
    (is (= #{[:p1 :b] [:p1 :c] [:p1 :a]} r3))
    (is (= #{[:p1 :b] [:p1 :c] [nil :a]} r3'))
    (is (= #{[:c :p1] [:b :p1] [:a :p1]} r4))
    (is (= #{[:c :p1] [:b :p1] [:a nil]} r4'))))

(deftest test-loop
  (let [g (assert-data empty-graph simple-loop-data)
        gm (assert-data empty-multi-graph simple-loop-data)]
    (data-loop g)
    (data-loop gm)))

(defn loop-path [g]
  (let [r1 (resolve-pattern g '[:a ?p* :c])
        r2 (resolve-pattern g '[:a ?p* :a])
        r3 (resolve-pattern g '[:a ?p+ :a])]
    (is (= [[[:p1 :p1]]] r1))
    (is (= [[[]]] r2))
    (is (= [[[:p1 :p1 :p1]]] r3))))

(deftest test-loop-path
  (let [g (assert-data empty-graph simple-loop-data)
        gm (assert-data empty-multi-graph simple-loop-data)]
    (loop-path g)
    (loop-path gm)))

(def dbl-branch-loop-data
  [[:a :p1 :b]
   [:b :p1 :c]
   [:b :p2 :c]
   [:b :p1 :d]
   [:d :p1 :a]
   [:c :p2 :e]
   [:e :p2 :b]
   [:d :p2 :f]
   [:g :p2 :h]])

(defn dbl-branch-loop [g]
  (let [r1 (unordered-resolve g '[:a :p1+ ?x])
        r1' (unordered-resolve g '[:a :p1* ?x])
        r2 (unordered-resolve g '[?x :p2+ :e])
        r2' (unordered-resolve g '[?x :p2* :e])
        r3 (unordered-resolve g '[:a ?p+ ?x])
        r3' (unordered-resolve g '[:a ?p* ?x])
        r4 (unordered-resolve g '[?x ?p+ :e])
        r4' (unordered-resolve g '[?x ?p* :e])
        r5 (unordered-resolve g '[:b :p1+ ?x])
        r5' (unordered-resolve g '[:b :p1* ?x])
        r6 (unordered-resolve g '[?x :p1+ :c])
        r6' (unordered-resolve g '[?x :p1* :c])
        r7 (unordered-resolve g '[:b ?p+ ?x])
        r7' (unordered-resolve g '[:b ?p* ?x])
        r8 (unordered-resolve g '[?x ?p+ :c])
        r8' (unordered-resolve g '[?x ?p* :c])]
    (is (= #{[:b] [:c] [:d] [:a]} r1))
    (is (= #{[:b] [:c] [:d] [:a]} r1'))
    (is (= #{[:c] [:b] [:e]} r2))
    (is (= #{[:c] [:b] [:e]} r2'))
    (is (= #{[:p1 :b] [:p1 :c] [:p1 :d] [:p1 :a] [:p1 :e] [:p1 :f]} r3))
    (is (= #{[:p1 :b] [:p1 :c] [:p1 :d] [nil :a] [:p1 :e] [:p1 :f]} r3'))
    (is (= #{[:c :p2] [:b :p2] [:a :p2] [:e :p2] [:d :p2]} r4))
    (is (= #{[:c :p2] [:b :p2] [:a :p2] [:e nil] [:d :p2]} r4'))
    (is (= #{[:c] [:d] [:a] [:b]} r5))
    (is (= #{[:c] [:d] [:a] [:b]} r5'))
    (is (= #{[:b] [:a] [:d]} r6))
    (is (= #{[:c] [:b] [:a] [:d]} r6'))
    (is (= #{[:p1 :c] [:p2 :c] [:p1 :d] [:p2 :d] [:p1 :e] [:p2 :e]
             [:p1 :b] [:p2 :b] [:p1 :f] [:p2 :f] [:p1 :a] [:p2 :a]} r7))
    (is (= #{[:p1 :c] [:p2 :c] [:p1 :d] [:p1 :e] [:p2 :e]
             [nil :b] [:p1 :f] [:p1 :a]} r7'))
    (is (= #{[:b :p1] [:b :p2] [:a :p1] [:a :p2] [:d :p1] [:d :p2]
             [:c :p1] [:c :p2] [:e :p1] [:e :p2]} r8))
    (is (= #{[:b :p1] [:b :p2] [:a :p1] [:a :p2] [:d :p1] [:d :p2]
             [:c nil] [:e :p1] [:e :p2]} r8'))))

(deftest test-dbl-branch-loop
  (let [g (assert-data empty-graph dbl-branch-loop-data)
        gm (assert-data empty-multi-graph dbl-branch-loop-data)]
    (dbl-branch-loop g)
    (dbl-branch-loop gm)))

(defn dbl-branch-loop-path [g]
  (let [r1 (resolve-pattern g '[:a ?p* :c])
        r2 (resolve-pattern g '[:a ?p* :f])
        r3 (resolve-pattern g '[:a ?p* :h])]
    (is (= [[[:p1 :p1]]] r1))
    (is (= [[[:p1 :p1 :p2]]] r2))
    (is (= [] r3))))

(deftest test-dbl-branch-loop-path
  (let [g (assert-data empty-graph dbl-branch-loop-data)
        gm (assert-data empty-multi-graph dbl-branch-loop-data)]
    (dbl-branch-loop-path g)
    (dbl-branch-loop-path gm)))


#?(:cljs (run-tests))
