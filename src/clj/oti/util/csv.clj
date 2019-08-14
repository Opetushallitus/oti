(ns oti.util.csv
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [ring.util.io :as ring-io]))

(defn csv-output [data]
  (let [headers (map name (keys (first data)))
        rows (map vals data)
        stream-csv (fn [ostream] (csv/write-csv ostream (cons headers rows) :separator \tab)
                                 (.flush ostream))]
    (ring-io/piped-input-stream #(stream-csv (io/make-writer % {})))))
