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

(deftest wrap-authorization-with-redirect-works
  (let [wrapped-handler (wrap-authorization (fn [_] :handled) :redirect)]
    (is (= {:status 302, :headers {"Location" "/oti/auth/cas?path=%2Foti%2Fvirkailija%2Fhenkilot"}, :body ""}
           (wrapped-handler {:uri "/oti/virkailija/henkilot"})))))

(deftest login-logout-works
  (let [ticket "barfoo"]
    (login ticket)
    (is (contains? @cas-tickets ticket))
    (logout ticket)
    (is (not (contains? @cas-tickets ticket)))))
