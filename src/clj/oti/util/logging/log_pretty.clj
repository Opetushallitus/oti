(ns oti.util.logging.log-pretty
  (:require [cheshire.core :as cheshire]
            [clojure.tools.logging :as log]))

(defn info
  [message json]
  (log/info (str message " " (cheshire/generate-string json {:pretty true}))))

