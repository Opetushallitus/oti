(ns oti.util.logging.rolling
  {:author "Modified from taoensso.timbre.appenders.3rd-party.rolling"}
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import  [java.text SimpleDateFormat]
            [java.util Calendar]))

(defn- rename-old-create-new-log [log old-log]
  (.renameTo log old-log)
  (.createNewFile log))

(defn- shift-log-period [log path prev-cal date-pattern file-format]
  (let [postfix (-> date-pattern SimpleDateFormat. (.format (.getTime prev-cal)))
        old-path (format file-format path postfix)
        old-log (io/file old-path)]
    (if (.exists old-log)
      (loop [index 0]
        (let [index-path (format "%s.%d" old-path index)
              index-log (io/file index-path)]
          (if (.exists index-log)
            (recur (+ index 1))
            (rename-old-create-new-log log index-log))))
      (rename-old-create-new-log log old-log))))

(defn- log-cal [date] (let [now (Calendar/getInstance)] (.setTime now date) now))

(defn- prev-period-end-cal [date pattern]
  (let [cal (log-cal date)
        offset (case pattern
                 :daily 1
                 :weekly (.get cal Calendar/DAY_OF_WEEK)
                 :monthly (.get cal Calendar/DAY_OF_MONTH)
                 0)]
    (.add cal Calendar/DAY_OF_MONTH (* -1 offset))
    (.set cal Calendar/HOUR_OF_DAY 23)
    (.set cal Calendar/MINUTE 59)
    (.set cal Calendar/SECOND 59)
    (.set cal Calendar/MILLISECOND 999)
    cal))

(defn- write-gzip [file]
  (with-open [w (-> (str (str/replace file ".gz" "") ".gz")
                    clojure.java.io/output-stream
                    java.util.zip.GZIPOutputStream.
                    clojure.java.io/writer)]
    (.write w (slurp file))))

(defn- gzip [file]
  (let [old-gzip-file (io/file (str file ".gz"))]
    (if (.exists old-gzip-file)
      (loop [index 0]
        (let [index-path (format "%s.%d.gz" (str file) index)
              index-file (io/file index-path)]
          (if (.exists index-file)
            (recur (inc index))
            (do (println index-file) (write-gzip index-file)))))
      (write-gzip file))))

(defn- size-str->bytes [size-limit]
  (let [match (re-matches #"(?i)(\d+\.?\d*)([GMK]?)B?$" (str/replace size-limit " " ""))
        [_ num-str precision] match]
    (try
      (if-not (str/blank? precision)
        (condp = (str/upper-case precision)
          "G" (-> (* (Double/parseDouble num-str) 1024 1024 1024) .intValue)
          "M" (-> (* (Double/parseDouble num-str) 1024 1024) .intValue)
          "K" (-> (* (Double/parseDouble num-str) 1024) .intValue))
        (Integer/parseInt num-str))
      (catch Exception e
        (throw (IllegalArgumentException. "Size limit string parsing failed. Please check size-limit option."))))))

(defn- not-gzipped-but-same [log path]
  (re-matches (re-pattern (str "(?!" log ".*\\.gz.*)" log ".*")) (str path)))

(defn- gzip-log-files [pred log]
  (let [logs (-> log .getParent io/file file-seq)]
    (doseq [f (filter (partial not-gzipped-but-same log) logs)]
      (when (pred f)
        (try
          (gzip f)
          (.delete f)
          (catch Exception _))))))

(defn- size-limit-exceeded? [size-limit log-file]
  (when-not (str/blank? size-limit)
    (let [log-size (.length log-file)
          size-limit (size-str->bytes size-limit)]
      (> log-size size-limit))))


(defn rolling-appender
  "Returns a Rolling file appender. Opts:
    :path         - logfile path.
    :pattern      - frequency of rotation, e/o {:daily :weekly :monthly}
    :date-pattern - Pattern given to Simpledateformat
    :size-limit   - Size string limit for triggering a roll e.g 10MB."
  [& [{:keys [path pattern date-pattern file-format size-limit gzip-pred]
       :or   {path    "./log-rolling.log"
              pattern :daily
              date-pattern "yyyyMMdd"
              file-format "%s.%s"}}]]

  {:enabled?   true
   :async?     false
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn
   (fn [data]
     (let [{:keys [instant output_]} data
           output-str (force output_)
           prev-cal   (prev-period-end-cal instant pattern)]
       (when-let [log (io/file path)]
         (try
           (when-not (.exists log)
             (io/make-parents log))
           (if (.exists log)
             (when (or (and size-limit (size-limit-exceeded? size-limit log))
                       (<= (.lastModified log) (.getTimeInMillis prev-cal)))
               (shift-log-period log path prev-cal date-pattern file-format))
             (.createNewFile log))
           (spit path (with-out-str (println output-str)) :append true)
           (when (fn? gzip-pred)
             (gzip-log-files gzip-pred log))
           (catch java.io.IOException _)))))})
