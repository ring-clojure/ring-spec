(ns ring.core.spec
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.string :as str]
            [ring.core.protocols :as p]))

(defn- lower-case? [s]
  (= s (str/lower-case s)))

(def ^:private lower-case-chars
  (map char (range 97 122)))

(def ^:private uri-chars
  (map char (concat (range 43 57) (range 65 90) (range 97 122) [95 126])))

(defn- gen-string [chars]
  (gen/fmap str/join (gen/vector (gen/elements chars))))

(defn- gen-query-string []
  (->> (gen/tuple (gen/not-empty (gen/string-alphanumeric)) (gen-string uri-chars))
       (gen/fmap (fn [[k v]] (str k "=" v)))
       (gen/vector)
       (gen/fmap #(str/join "&" %))))

(defn- gen-method []
  (gen/fmap keyword (gen/not-empty (gen-string lower-case-chars))))

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

(s/def :ring.request/method
  (-> (s/and keyword? (comp lower-case? name))
      (s/with-gen gen-method)))

(s/def :ring.request/protocol
  (s/with-gen string? #(gen/return "HTTP/1.1")))

(s/def :ring.request/ssl-client-cert #(instance? java.security.cert.X509Certificate %))

(s/def :ring.request/header-name     (s/and string? lower-case?))
(s/def :ring.request/header-value    string?)
(s/def :ring.request/headers         (s/map-of :ring.request/header-name
                                               :ring.request/header-value))

(s/def :ring.request/body            #(instance? java.io.InputStream %))

(s/def :ring.request/request-method  :ring.request/method)

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
                   :ring.request/ssl-client-cert
                   :ring.request/body]))

;; Response

(s/def :ring.response/status       (s/int-in 100 600))

(s/def :ring.response/header-name  string?)
(s/def :ring.response/header-value (s/or :one string? :many (s/coll-of string?)))
(s/def :ring.response/headers      (s/map-of :ring.response/header-name
                                             :ring.response/header-value))

(s/def :ring.response/body         #(satisfies? p/StreamableResponseBody %))

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
         :raise   (s/fspec :args (s/cat :exception #(instance? Throwable %)) :ret any?)))

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
  :ret  (s/or :async :ring.sync.handler/ret  :async :ring.async.handler/ret)
  :fn   (s/or :sync  (s/keys :req-un [:ring.sync.handler/args :ring.sync.handler/ret])
              :async (s/keys :req-un [:ring.async.handler/args :ring.async.handler/ret])))

(s/def :ring/handler
  (s/or :sync :ring.sync/handler
        :async :ring.async/handler
        :sync+async :ring.sync+async/handler))
