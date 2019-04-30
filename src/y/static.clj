(ns y.static
  (:require [y.server :as s]
            [clojure.java.io :as io]))


(defn write-page []
  (let [p "public/index.html"
        f (io/file p)]
    (io/make-parents f)
    (spit f (s/build-page))))

(defn -main [& args]
  (write-page))
