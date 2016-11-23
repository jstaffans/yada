(ns ^{:author "Johannes Staffans"}
 yada.content-encoding-test
  (:require [byte-streams :as bs]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [manifold.deferred :as d]
            [ring.mock.request :refer [header request]]
            [cheshire.core :as json]
            [yada
             [interceptors :as i]
             [yada :refer [resource yada]]])
  (:import java.io.ByteArrayOutputStream
           [java.util.zip GZIPInputStream GZIPOutputStream]))

(defn gzip
  [in out]
  (with-open [gzipped-out (-> out GZIPOutputStream.)]
    (io/copy in gzipped-out)))

(defn to-gzipped-byte-array
  [content]
  (let [baos    (ByteArrayOutputStream.)
        _       (gzip content baos)]
    (.toByteArray baos)))

(defn post-text-plain-resource
  []
  (resource
   {:methods
    {:post
     {:produces "text/plain"
      :consumes "text/plain"
      :response (fn [ctx] (:body ctx))}}}))

(deftest content-encoding-test
  (testing "Non-gzipped body"
    (let [handler  (yada (post-text-plain-resource))
          content  "test body"
          response @(handler (-> (request :post "/" content)
                                 (header "Content-type" "text/plain")))]
      (is (= 200 (-> response :status)))
      (is (= "test body" (-> response :body bs/to-string)))))

  (testing "Gzipped body"
    (let [handler  (yada (post-text-plain-resource))
          content  (to-gzipped-byte-array "test body")
          response @(handler (-> (request :post "/" content)
                                 (header "Content-type" "text/plain")
                                 (header "Content-encoding" "gzip")))]
      (is (= 200 (-> response :status)))
      (is (= "test body" (-> response :body bs/to-string)))))

  (testing "Unsupported Content-encoding"
    (let [handler  (yada (post-text-plain-resource))
          content  (to-gzipped-byte-array "test body")
          response @(handler (-> (request :post "/" content)
                                 (header "Content-type" "text/plain")
                                 (header "Content-encoding" "deflate")))]
      (is (= 415 (-> response :status))))))
