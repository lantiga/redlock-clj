(ns redlock-clj.core-test
  (:require [clojure.test :refer :all]
            [redlock-clj.core :refer :all]))

(defn file-based-counter [{:keys [cluster file-name times-per-thread n-threads]}] 
  (spit file-name (str 0)) 
  (doall 
    (pmap 
      (fn [_] 
        (loop [i 0]
          (when (< i times-per-thread)
            (if-let [counter-lock (lock! cluster file-name 1000)]
              (if (> (:validity counter-lock) 500)
                (do
                  (->> (slurp file-name)
                    Long/parseLong inc str
                    (spit file-name)) 
                  (unlock! cluster counter-lock) 
                  (recur (inc i))) 
                (do 
                  (unlock! cluster counter-lock)
                  (recur i))) 
              (recur i)))))
      (range n-threads))) 
  (->> (slurp file-name) Long/parseLong))

(deftest counter-test
  (testing "File-based counter." 
     (is (= (file-based-counter 
              {:cluster [{:spec {:port 6379}}
                         {:spec {:port 6380}}
                         {:spec {:port 6381}}]
               :file-name "/tmp/counter5.txt"
               :times-per-thread 100
               :n-threads 5})
            500))))

(comment
  (run-tests))
