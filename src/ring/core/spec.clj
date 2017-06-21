(ns ring.core.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [ring.core.protocols :as p]
            [ring.util.parsing :as parse]))

(defn- lower-case? [s]
  (= s (str/lower-case s)))

(defn- trimmed? [s]
  (= s (str/trim s)))

(defn- char-range [a b]
  (map char (range (int a) (inc (int b)))))

(def ^:private lower-case-chars
  (set (char-range \a \z)))

(def ^:private alphanumeric-chars
  (set (concat (char-range \A \Z) (char-range \a \z) (char-range \0 \9))))

(def ^:private uri-chars
  (into alphanumeric-chars #{\- \. \_ \~ \/ \+ \,}))

(def ^:private field-name-chars
  (into alphanumeric-chars #{\! \# \$ \% \& \' \* \+ \- \. \^ \_ \` \| \~}))

(def ^:private whitespace-chars
  #{0x09 0x20})

(def ^:private visible-chars
  (set (map char (range 0x21 (inc 0x7e)))))

(def ^:private obs-text-chars
  (set (map char (range 0x80 (inc 0xff)))))

(def ^:private field-value-chars*
  (into whitespace-chars visible-chars))

(def ^:private field-value-chars
  (into field-value-chars* obs-text-chars))

(defn- field-name-chars? [s]
  (every? field-name-chars s))

(defn- field-value-chars? [s]
  (every? field-value-chars s))

(defn- gen-string [chars]
  (gen/fmap str/join (gen/vector (gen/elements chars))))

(defn- gen-query-string []
  (->> (gen/tuple (gen/not-empty (gen/string-alphanumeric)) (gen-string uri-chars))
       (gen/fmap (fn [[k v]] (str k "=" v)))
       (gen/vector)
       (gen/fmap #(str/join "&" %))))

(defn- gen-method []
  (gen/fmap keyword (gen/not-empty (gen-string lower-case-chars))))

(defn- gen-input-stream []
  (gen/fmap #(java.io.ByteArrayInputStream. %) (gen/bytes)))

(defn- gen-exception []
  (gen/fmap (fn [s] (Exception. s)) (gen/string-alphanumeric)))

;; Internal

(s/def :ring.core/error
  (-> #(instance? Throwable %) (s/with-gen gen-exception)))

(s/def :ring.http/field-name
  (-> (s/and string? not-empty field-name-chars?)
      (s/with-gen #(gen/not-empty (gen-string field-name-chars)))))

(s/def :ring.http/field-value
  (-> (s/and string? field-value-chars? trimmed?)
      (s/with-gen #(gen/fmap str/trim (gen-string field-value-chars*)))))

;; Request

(s/def :ring.request/server-port (s/int-in 1 65535))
(s/def :ring.request/server-name string?)
(s/def :ring.request/remote-addr string?)

(s/def :ring.request/uri
  (-> (s/and string? #(str/starts-with? % "/"))
      (s/with-gen (fn [] (gen/fmap #(str "/" %) (gen-string uri-chars))))))

(s/def :ring.request/query-string
  (s/with-gen string? gen-query-string))

(s/def :ring.request/scheme #{:http :https})

(s/def :ring.request/request-method
  (-> (s/and keyword? (comp lower-case? name))
      (s/with-gen gen-method)))

(s/def :ring.request/protocol
  (s/with-gen string? #(gen/return "HTTP/1.1")))

(s/def :ring.request/header-name
  (-> (s/and :ring.http/field-name lower-case?)
      (s/with-gen #(gen/fmap str/lower-case (s/gen :ring.http/field-name)))))

(s/def :ring.request/header-value :ring.http/field-value)

(s/def :ring.request/headers
  (s/map-of :ring.request/header-name :ring.request/header-value))

(s/def :ring.request/body
  (s/with-gen #(instance? java.io.InputStream %) gen-input-stream))

(s/def :ring/request
  (s/keys :req-un [:ring.request/server-port
                   :ring.request/server-name
                   :ring.request/remote-addr
                   :ring.request/uri
                   :ring.request/scheme
                   :ring.request/protocol
                   :ring.request/headers
                   :ring.request/request-method]
          :opt-un [:ring.request/query-string
                   :ring.request/body]))

;; Response

(s/def :ring.response/status (s/int-in 100 600))

(s/def :ring.response/header-name :ring.http/field-name)

(s/def :ring.response/header-value
  (s/or :one :ring.http/field-value :many (s/coll-of :ring.http/field-value)))

(s/def :ring.response/headers
  (s/map-of :ring.response/header-name :ring.response/header-value))

(s/def :ring.response/body
  (-> #(satisfies? p/StreamableResponseBody %)
      (s/with-gen #(gen/one-of [(gen/return nil)
                                (gen/string-ascii)
                                (gen/list (gen/string-ascii))
                                (gen-input-stream)]))))

(s/def :ring/response
  (s/keys :req-un [:ring.response/status
                   :ring.response/headers]
          :opt-un [:ring.response/body]))

;; Handler

(s/def :ring.sync.handler/args
  (s/cat :request :ring/request))

(s/def :ring.async.handler/args
  (s/cat :request :ring/request
         :respond (s/fspec :args (s/cat :response :ring/response) :ret any?)
         :raise   (s/fspec :args (s/cat :error :ring.core/error) :ret any?)))

(s/def :ring.sync.handler/ret  :ring/response)
(s/def :ring.async.handler/ret any?)

(s/fdef :ring.sync/handler
  :args :ring.sync.handler/args
  :ret  :ring.sync.handler/ret)

(s/fdef :ring.async/handler
  :args :ring.async.handler/args
  :ret  :ring.async.handler/ret)

(s/fdef :ring.sync+async/handler
  :args (s/or :sync  :ring.sync.handler/args :async :ring.async.handler/args)
  :ret  (s/or :sync  :ring.sync.handler/ret  :async :ring.async.handler/ret)
  :fn   (s/or :sync  (s/keys :req-un [:ring.sync.handler/args :ring.sync.handler/ret])
              :async (s/keys :req-un [:ring.async.handler/args :ring.async.handler/ret])))

(s/def :ring/handler
  (s/or :sync :ring.sync/handler
        :async :ring.async/handler
        :sync+async :ring.sync+async/handler))
