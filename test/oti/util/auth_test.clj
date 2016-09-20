(ns oti.util.auth-test
  (:require [clojure.test :refer :all]
            [oti.util.auth :refer :all]))

(deftest wrap-authorization-works
  (let [wrapped-handler (wrap-authorization (fn [_] :handled))
        ticket "foobar"
        request {:session {:identity {:ticket ticket}}}]
    (login ticket)
    (is (= :handled (wrapped-handler request)))
    (logout ticket)
    (is (= 401 (:status (wrapped-handler request))))
    (is (= 401 (:status (wrapped-handler {}))))))
