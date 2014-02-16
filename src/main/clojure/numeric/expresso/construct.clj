(ns numeric.expresso.construct
  (:refer-clojure :exclude [==])
  (:use [numeric.expresso.properties]
        [clojure.core.logic])
  (:require [clojure.walk :as walk]
            [clojure.set :as set]
            [numeric.expresso.protocols :as protocols]
            [numeric.expresso.impl.pimplementation :as impl]
            [numeric.expresso.types :as types]
            [clojure.core.matrix :as mat]
            [clojure.core.matrix.operators :as mop]
            [numeric.expresso.utils :as utils])
  (:import [numeric.expresso.impl.pimplementation PolynomialExpression
            BasicExtractor]))
(declare ce cev create-normal-expression)

;;This is the namespace for constructing expressions. Its main function is
;;ce (for create expression) which behaves like list* but adds important meta
;;data to the expression which expresso uses for its manipulations.
;;The constructing is also based on multimethod dispatch so it can be extended
;;to custom operators easily
;;I recommend reading the ce functions first.


;;the shape of the inner-product expression is dependend on the first
;;and last position of a shape which is not just []

(defn- first-last-pos-mshape
  "finds the first and the last position of nun-number-shaped arg in args"
  [args]
  (let [args (vec args)
        n (count args)
        f (loop [i 0] (if (< i n) (if (< (count (nth args i)) 2)
                                    (recur (inc i)) i) nil))
        l (loop [i (dec n)] (if (<= 0 i) (if (< (count (nth args i)) 2)
                                           (recur (dec i)) i) nil))]
    (and f l [f l])))



(defn- inner-product-shape
  "calculates the shape of an inner-product expression"
  [& symb]
  (if-let [[f l] (first-last-pos-mshape symb)]
    (if (= f l) (if (or (= 0 f) (= (dec (count symb)) f))
                  (nth symb f) [])
        (vec (concat (butlast (nth symb f)) (rest (nth symb l)))))
    (let [vs (remove empty? symb)]
      (if (even? (count vs)) [] (last vs)))))

;;special constructing function for the inner-product. Constructs the expression
;;in the normal way and sets its shape to the (evaled if possible) shape
;;expression

(defn- create-inner-product
  "creates an inner-product expression"
  [[symb args]]
  (let [shapes (map protocols/shape args)
        expr (create-normal-expression symb args)]
    (-> expr (protocols/set-shape 
              (impl/eval-if-determined
               (create-normal-expression
                `inner-product-shape shapes))))))


(defn- longest-shape
  "returns the longest-shape of its args"
  [& args]
  (first (sort-by (comp - count) args)))

;;creates the elemwise-operations from core.matrix with the shape computed
;;with the implicit broadcasting semantics from core.matrix
(defn create-elemwise-operation [symb args]
  (let [shapes (map protocols/shape args)]
    (-> (create-normal-expression symb args)
        (protocols/set-shape
         (if-not (some #(or (lvar? %) (symbol? %) (protocols/expr-op %)) shapes)
           (first (sort-by (comp - count) shapes))
           (create-normal-expression `longest-shape shapes))))))
    

;;constructing dispatch for known symbols to expresso
      
(defmulti create-special-expression
  "dispatch method for expression construction used for ce"
  first)
(defmethod create-special-expression :default [_]  nil)
(defmethod create-special-expression 'inner-product [x]
  (create-inner-product x))
(defmethod create-special-expression 'mmul [x]
  (create-inner-product x))
(defmethod create-special-expression 'negate [[symb args]]
  (create-elemwise-operation '- args))
(defmethod create-special-expression 'add [[symb args]]
  (create-elemwise-operation '+ args))
(defmethod create-special-expression 'sub [[symb args]]
  (create-elemwise-operation '- args))
(defmethod create-special-expression 'emul [[symb args]]
  (create-elemwise-operation '* args))
(defmethod create-special-expression 'mul [[symb args]]
  (create-elemwise-operation '* args))
(defmethod create-special-expression 'div [[symb args]]
  (create-elemwise-operation '/ args))
(defmethod create-special-expression '+ [[symb args]]
  (create-elemwise-operation '+ args))
(defmethod create-special-expression '- [[symb args]]
  (create-elemwise-operation '- args))
(defmethod create-special-expression '* [[symb args]]
  (create-elemwise-operation '* args))
(defmethod create-special-expression '/ [[symb args]]
  (create-elemwise-operation '/ args))

;;An example of what this dispatch allows. This is te construction of a
;;sum expression so that (sum k 0 5 k) means (sum k (<= 0 k 5) k)
;;the also the execute function for sum expressions and the
;;emit-code function in properties.clj

(defmethod create-special-expression 'sum [[symb args]]
  (let [args (vec args)]
    (case (count args)
      3 (create-normal-expression 'sum args)
      4 (create-normal-expression 'sum [(nth args 0)
                                        (create-normal-expression
                                         '<= [(nth args 1) (nth args 0)
                                              (nth args 2)])
                                        (nth args 3)]))))
      

;;expresso construct symbols from the whole namespace qualified name of a
;;symbol. For known symbols expresso uses short symbols according to this
;;multimethods
(defmulti expresso-name
  "transforms the fully-qualified symbol to a short symbol used in expresso"
  identity)
(defmethod expresso-name :default [s]
  (if (= (str s) "clojure.core//") '/ s))
(defmethod expresso-name 'clojure.core/* [_] '*)
(defmethod expresso-name 'clojure.core/+ [_] '+)
(defmethod expresso-name 'clojure.core/- [_] '-)
(defmethod expresso-name 'clojure.core// [_] '/)
(defmethod expresso-name `= [_] '=)
(defmethod expresso-name 'numeric.expresso.core/** [_] '**)
(defmethod expresso-name `mop/* [_] '*)
(defmethod expresso-name `mop/+ [_] '+)
(defmethod expresso-name `mop/- [_] '-)
(defmethod expresso-name `mat/emul [_] '*)
(defmethod expresso-name `mat/div [_] '/)
(defmethod expresso-name `mat/add [_] '+)
(defmethod expresso-name `mat/sub [_] '-)
(defmethod expresso-name 'Math/abs [_] 'abs)
(defmethod expresso-name 'Math/acos [_] 'acos)
(defmethod expresso-name 'Math/asin [_] 'asin)
(defmethod expresso-name 'Math/atan [_] 'atan)
(defmethod expresso-name 'Math/cos [_] 'cos)
(defmethod expresso-name 'Math/cosh [_] 'cosh)
(defmethod expresso-name 'Math/exp [_] 'exp)
(defmethod expresso-name 'Math/log [_] 'log)
(defmethod expresso-name 'Math/log10 [_] 'log10)
(defmethod expresso-name 'Math/sin [_] 'sin)
(defmethod expresso-name 'Math/sinh [_] 'sinh)
(defmethod expresso-name 'Math/sqrt [_] 'sqrt)
(defmethod expresso-name 'Math/tan [_] 'tan)
(defmethod expresso-name 'Math/tanh [_] 'tanh)
(defmethod expresso-name 'mat/negate [_] '-)
(defmethod expresso-name `mat/mul [_] 'mul)
(defmethod expresso-name `mat/inner-product [_] 'inner-product)

;;single expression creation
(defn- create-expression
  "constructing function for Expression Datatype"
  [symbol args]
  (numeric.expresso.impl.pimplementation.Expression. symbol (vec args)))

(defn create-extractor
  "creates an expreator from the symbol and arguments"
  [symb args]
  (when-let [rel (extractor-rel symb)]
    (numeric.expresso.impl.pimplementation.BasicExtractor. symb args rel)))

;;adds metadata to a symbol in the expression and adds type and shape information
;;in it. by default it is assumed to be a number.
;;if it has a type key in its metadata than the corresponging expresso type
;;is used. this makes it possible to construct a matrix symbol very easy like in
;;(ex (+ a ^:matrix b)) where a is a normal number and b is a matrix of undeter-
;;mined shape

(defn construct-symbol
  "adds metadata the the symbol which occurs in an unprocessed expression"
  [arg]
  (let [type (cond (:matrix (meta arg)) types/matrix
                   (:number (meta arg)) types/number
                   (:double (meta arg)) types/double
                   (:long   (meta arg)) types/long
                   (:int    (meta arg)) types/integer
                   :else (if (sequential? (:shape (meta arg)))
                                          types/matrix
                                          types/number))
        shape (cond (isa? type types/number) []
                    (= type types/matrix) (or (:shape (meta arg))
                                              (lvar 'shape))
                    :else (lvar 'shape))]
    (with-meta arg (merge {:type type :shape shape} (meta arg)))))

;;creates a list of (op args*). Creates a real instance of PersistentList.
;;This is the fastest way to construct the Persistent list after some
;;experiments. It adds the metadata to the symbol. See properties.clj for details

(defn create-normal-expression
  "creates a metadata attached list of (symb args*)"
  [symb args]
  (into '() (concat (reverse args) [(with-meta symb (add-information symb))])))

;;Main construction function for expressions.
;;uses the short name for the fully namespace qualified symbol if expresso
;;knows about the expression. It adds the right metadata to all symbols in the
;;argument list. It than uses the dispatch functions create-special-expression
;;and create-extractor to construct the expression, backing up to just creating
;;the expression with create-normal-expression

(defn ce
  "constructs an expression from the symbol with the supplied args"
  [symb & args]
  (let [symb (expresso-name symb)
        args (map #(if (symbol? %) (construct-symbol %) %) args)]
    (or (create-special-expression [symb args])
        (create-extractor symb args)
        (create-normal-expression symb args))))

;;same as ce but doesn't take variable args
(defn cev
  "same as ce. Treats args as vector"
  [symb args]
  (apply (partial ce symb) args))

;;explicit constructing functions for symbols to use in expressions.
;;not explicitly neccessary for the most cases but very useful if one
;;particular (matrix) symbol is used in many places in the expression

(defn expresso-symb
  "creates a symbol for further use in expresso
   adds the shape, type and properties specified"
  [symb & {:keys [shape type properties]
           :or {shape (lvar 'shape)
                type types/number
                properties #{}}}]
  (let [meta
        (cond
         (= type types/number)
         (if (or (lvar? shape) (= shape []))
           {:shape [] :type type :properties properties}
           {:shape shape :type types/matrix :properties properties})
         :else {:shape shape :type type :properties properties})]
    (construct-symbol (with-meta symb meta))))


(defn matrix-symb
  "creates a symbol representing a matrix in expresso"
  [symb &{:keys [shape properties]
          :or {shape (lvar 'shape)
               properties #{}}}]
  (expresso-symb symb :shape shape :properties properties :type types/matrix))

(defn zero-matrix
  "creates a symbol representing a zero-matrix in expresso"
  [& {:keys [shape symb properties]
      :or {shape (lvar 'shape)
           symb (gensym "zeromat")
           properties #{:mzero}}}]
  (expresso-symb symb :shape shape :type types/matrix
                 :properties (set/union #{:mzero} properties)))

(defn identity-matrix
  "creates a symbol representing an identity-matrix in expresso"
  [& {:keys [shape symb properties]
      :or {shape (lvar 'shape)
           symb (gensym "identitymat")
           properties #{:midentity}}}]
  (expresso-symb symb :shape shape :type types/matrix
                 :properties (set/union #{:midentity} properties)))

;;; The rule based translator matches the operator symbol with respect to
;;; clojures hierarchy semantics, so a rule written for 'e/ca-op matches all
;;; commutative-associative operators -- this hierarchies aren't used very much
;;; in expresso up to now

(derive 'e/ca+ 'e/ca-op)
(derive 'e/ca* 'e/ca-op)
(derive 'e/+   'e/ca+)
(derive 'e/*   'e/ca*)
(derive 'e/add   'e/ca-op)
(derive 'clojure.core/+ 'e/ca+)
(derive 'clojure.core/* 'e/ca*)
(derive 'clojure.core/- 'e/-)
(derive 'clojure.core// 'e/div)
(derive 'clojure.core.matrix.operators/+ 'e/ca-op)
(derive 'clojure.core.matrix/add 'e/ca-op)
(derive `° 'e/ao-op)

;;constructing and manipulation functions for sequential-matchers
;;The rule based translator has a feature called seq-matching where one
;;symbol starting with ?& can match a whole sequence from the expression.
;;Internally seq-matchers are represented by a vector of [::seq-match data]

;;The functions seq-matcher and matcher-args are used to construct and
;;deconstruct seq-matchers
;;There are also a few utility functions which wrap ad unwrap the seq-matcher
;;like map-sm zip-sm, ... they can be recognized by the -sm suffix
(defn seq-matcher
  "constructs a sequential-matcher containing the specified data"
  [data]
  [::seq-match data])

(defn matcher-args
  "returns the data of the seq-matcher"
  [seq-matcher]
  (if (and (sequential? seq-matcher) (= (first seq-matcher) ::seq-match))
    (second seq-matcher)
    [seq-matcher]))

(defn zip-sm
  "zips the seq-matcher with the specified colls"
  [sm & colls]
  (apply (partial map (fn [& a] a) (matcher-args sm)) colls))

(defn map-sm
  "maps the content of the sec-matchers with func"
  [func & sm]
  (->> sm (map matcher-args) (apply (partial map func)) seq-matcher))

(defn first-sm
  "returns the first element of the seq-matcher"
  [sm]
  (first (matcher-args sm)))
(defn rest-sm
  "returns the rest of the seq-matcher data"
  [sm]
  (seq-matcher (rest (matcher-args sm))))

(defn last-sm
  "returns the last element of the seq-matcher data"
  [sm]
  (last (matcher-args sm)))

(defn count-sm
  "returns the count of the seq-matcher data"
  [sm]
  (count (vec (matcher-args sm))))

(defn split-in-pos-sm
  "splits the seq-matcher data to [left pos-elem right]"
  [sm pos]
  (let [args (vec (matcher-args sm))]
    [(seq-matcher (subvec args 0 pos))
     (nth args pos)
     (seq-matcher (subvec args (+ pos 1) (count args)))]))


;;Because ce only constructs one level of an expression one would have to
;;construct expressions like this: (ce '+ 1 2 (ce '- 3 4)). The higher level
;;construction macros presented here allow you to construct expressions like this
;;(ex (+ 1 2 (- 3 4))) by expanding into the appropriate calls to ce
;;ex does implicit quoting while ex' does implifit quoting both allow for ~
;;to be used in the supplied list

(defn- var-to-symbol [v]
  (let [s (str v)
        erg (-> (.substring s 2 (.length s)) symbol)]
    erg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;construct-with macro. This macro kaves a symbol vector and encloses arbitrary
;;code. The enclosed code is then walked and every occurrence of one symbol in
;;the symbol vector which is in function position in the code is replaced by a
;;call to ce
;;especially useful when constructing rules.
;;This macro is a workaround about some annoyances with clojures namespaces
;;using this macro has the same effect as using the functions in
;;numeric.expresso.constructing-functions specified in the symbol vector for
;;the enclosed code and has no other distorting effect on code outside of the
;;enclosing code. This is more convenient than using the constructing-functions
;;and having to use aliases for the clojure +,-,*,/,= for the whole rest of
;;the namespace.
(defn- replace-with-expresso-sexp
  "if s-exp has op in symbs replace if be call to ce function"
  [symbs s-exp]
  (if (and (seq? s-exp) (symbs (first s-exp)))
    (let [f (first s-exp)
          symb (if-let [r (resolve f)] (var-to-symbol r) f)]
      (list* `ce (list 'quote symb) (rest s-exp)))
    s-exp))

(defn- construct-with*
  "replaces all calls to functions in symbv with calls to ce"
  [symbv & code]
  (let [s-set (set symbv)]
    `(do 
       ~@(clojure.walk/postwalk #(replace-with-expresso-sexp s-set %) code))))

(defmacro construct-with
  "replaces all calls to functions in symbv with expression constructing
   functions."
  [symbv & code]
  (apply (partial construct-with* symbv) code))


;;the ex' macro replaces in its body all function position operators with
;;calls to ce. The operators are fully namespace-qualified when calling ce.
;;It does not automatically quote a symbol in the expr
(declare resolve-op)

(defn- create-expression-with-explicit-quoting
  [s expr]
  (if (and (seq? expr) (symbol? (first expr)) (not= (first expr) 'quote))
    (if (= 'clojure.core/unquote (first expr))
      (second expr)
      (list* `ce (list 'quote (resolve-op (first expr)))
             (map #(create-expression-with-explicit-quoting s %) (rest expr))))
    (if (s expr) (list 'quote expr) expr)))

(defn ex'*
  "function version of ex'"
  [& expr]
  (let [[s expr]
        (if (= (count expr) 1)
          [#{} (first expr)]
          [(into #{} (first expr)) (second expr)])]
    (create-expression-with-explicit-quoting s expr)))

(defmacro ex'
  "creates an expression with explicit quoting."
  [& expr]
  (apply ex'* expr))

;;The ex macro
;;Like the ex' macro but implicitly quotes the arguments. It is the most useful
;;and most used construction macro so far.

(defn- resolve-op
  "resolves the operator op"
  [op]
  (if-let [r (resolve op)] (var-to-symbol r) op))


(defn- create-expression-with-implicit-quoting
  "creates an expression with implicit quoting"
  [expr]
  (if (and (seq? expr) (symbol? (first expr)))
    (if (= 'clojure.core/unquote (first expr))
      (second expr)
      (list* `ce (list 'quote (resolve-op (first expr)))
             (map create-expression-with-implicit-quoting (rest expr))))
    (list 'quote expr)))

(defn ex*
  "function version of ex"
  [expr]
  (create-expression-with-implicit-quoting expr))

(defmacro ex
  "creates expression with implicit quoting of variables"
  [expr]
  (ex* expr))


(defn let-expr
  "creates a let-expression with specified bindings and code vector"
  [bindings code]
  (numeric.expresso.impl.pimplementation.LetExpression. bindings code))

(defn to-expression
  "converts the given expr, which consists of normal clojure s-expr
   to s-expr with expresso metadata added, so that it can be handled
   by the manipulation functions in expr. Is called on the input of all
   numeric.expresso.core functions. Does nothing if expr is already a
   valid expresso expression"
  [expr]
  (if-let [op (protocols/expr-op expr)]
    expr
    (walk/postwalk #(if (and (seq? %) (symbol? (first %)))
                      (apply (partial ce (first %))  (rest %))
                      %) expr)))

(defn extractor?
  "checks whether x is an extractor"
  [x]
  (instance? BasicExtractor x))