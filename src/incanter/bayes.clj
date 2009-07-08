;;; bayes.clj -- Bayesian estimation library for Clojure

;; by David Edgar Liebke http://incanter.org
;; March 11, 2009

;; Copyright (c) David Edgar Liebke, 2009. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

;; CHANGE LOG
;; March 11, 2009: First version



(ns incanter.bayes 
  (:use [incanter.core :only (matrix mmult mult div minus trans ncol nrow 
                              plus to-list decomp-cholesky solve half-vectorize
                              symmetric-matrix)] 
        [incanter.stats :only (sample-normal sample-gamma sample-dirichlet
                               sample-inv-wishart sample-mvn mean)]))





(defn sample-model-params 
" Returns a sample of the given size of the the parameters (coefficients and
  error variance) of the given linear-model. The sample is generated using 
  Gibbs sampling.

  See also:
    incanter.stats/linear-model

  Examples:
    (use '(incanter core datasets stats charts bayes))

    (def ols-data (to-matrix (get-dataset :survey)))
    (def x (sel ols-data (range 0 2313) (range 1 10)))
    (def y (sel ols-data (range 0 2313) 10))
    (def lm (linear-model y x :intercept false))
    (def param-samp (sample-model-params 5000 lm))
    
    ;; view trace plots
    (view (trace-plot (:var param-samp ))) 
    (view (trace-plot (sel (:coefs param-samp) :cols 0)))

    ;; view histograms
    (view (histogram (:var param-samp))) 
    (view (histogram (sel (:coefs param-samp) :cols 0)))

    ;; calculate statistics
    (map mean (trans (:coefs param-samp)))
    (map median (trans (:coefs param-samp)))
    (map sd (trans (:coefs param-samp)))

    ;; show the 95% bayesian confidence interval for the firt coefficient
    (quantile (sel (:coefs param-samp) :cols 0) :probs [0.025 0.975])

"
  ([size linear-model]
    (let [x (:x linear-model)
          y (:y linear-model)
          pars (:coefs linear-model)
          xtxi (solve (mmult (trans x) x))
          resid (:residuals linear-model)
          shape (/ (- (nrow x) (ncol x)) 2)
          rate (mult 1/2 (mmult (trans resid) resid))
          s-sq (div 1 (sample-gamma size :shape shape :rate rate))]
      {:coefs 
        (matrix 
          ;(pmap ;; run a parallel map over the values of s-sq
          (map
            (fn [s2] 
              (to-list (plus (trans pars)
                  (mmult (trans (sample-normal (ncol x))) 
                    (decomp-cholesky (mult s2 xtxi))))))
            (to-list (trans s-sq)))) 
      :var s-sq})))




(defn sample-proportions
" sample-proportions has been renamed sample-multinomial-params"
  ([size counts] 
   (throw (Exception. "sample-proportions has been renamed sample-multinomial-params"))))



(defn sample-multinomial-params
" Returns a sample of multinomial proportion parameters.
  The counts are assumed to have a multinomial distribution.
  A uniform prior distribution is assigned to the multinomial vector
  theta, then the posterior distribution of theta is
  proportional to a dirichlet distribution with parameters 
  (plus counts 1).


  Examples:
    (use '(incanter core stats bayes charts))

    (def  samp-props (sample-multinomial-params 1000 [727 583 137]))

    ;; view means, 95% CI, and histograms of the proportion parameters
    (mean (sel samp-props :cols 0))
    (quantile (sel samp-props :cols 0) :probs [0.0275 0.975])
    (view (histogram (sel samp-props :cols 0)))
    (mean (sel samp-props :cols 1))
    (quantile (sel samp-props :cols 1) :probs [0.0275 0.975])
    (view (histogram (sel samp-props :cols 1)))
    (mean (sel samp-props :cols 2))
    (quantile (sel samp-props :cols 2) :probs [0.0275 0.975])
    (view (histogram (sel samp-props :cols 2)))
    
    ;; view  a histogram of the difference in proportions between the first
    ;; two candidates
    (view (histogram (minus (sel samp-props :cols 0) (sel samp-props :cols 1))))
    

    
"
  ([size counts]
    (sample-dirichlet size (plus counts 1))))




(defn sample-mvn-params
" Returns samples of means (sampled from an mvn distribution) and vectorized covariance 
  matrices (sampled from an inverse-wishart distribution) for the given mvn data.

  Arguments:
    size -- the number of samples to return
    y -- the data used to estimate the parameters


  Returns map with following fields:
    :means
    :sigmas


  Examples:

    (use '(incanter core stats bayes charts))
    (def y (sample-mvn 500 :sigma (identity-matrix 2)))
    (def samp (sample-mvn-params 1000 y))

    (map mean (trans (:means samp)))
    (symmetric-matrix (map mean (trans (:sigmas samp))) :by-row false)

    (view (histogram (sel (:means samp) :cols 0) :x-label \"mean 1\"))
    (view (histogram (sel (:means samp) :cols 1) :x-label \"mean 2\"))
    (view (histogram (sel (:sigmas samp) :cols 1) :x-label \"covariance\"))
    (view (histogram (sel (:sigmas samp) :cols 0) :x-label \"variance 1\"))
    (view (histogram (sel (:sigmas samp) :cols 2) :x-label \"variance 2\"))

    (map #(quantile % :probs [0.025 0.0975]) (trans (:means samp)))
    (map #(quantile % :probs [0.025 0.0975]) (trans (:sigmas samp)))




"
  ([size y & options]
    (let [opts (if options (apply assoc {} options) nil)
          means (map mean (trans y))
          n (count y)
          S (reduce plus 
                    (map #(mmult (minus (to-list %) means) 
                                 (trans (minus (to-list %) means))) 
                         y))
          sigma-samp (matrix (for [_ (range size)] 
                               (half-vectorize (sample-inv-wishart :df (dec n) :scale (solve S)))))
          mu-samp (matrix (for [sigma sigma-samp]
                            (sample-mvn 1 
                                                        :mean means 
                                                        :sigma (div (symmetric-matrix sigma :by-row false) n))))
          ]
  {:means mu-samp :sigmas sigma-samp})))
          

