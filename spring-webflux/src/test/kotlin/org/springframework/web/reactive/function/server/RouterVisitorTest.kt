package org.springframework.web.reactive.function.server

import org.junit.Assert
import org.junit.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType

class RouterVisitorTest {

	class Predicate : RequestPredicates()

	private fun visitor(block: RouterFunctionDsl.() -> Unit): RouteVisitor =
		router {
			block()
		}.let { rf ->
			RouteVisitor().also { rv ->
				rf.accept(rv)
			}
		}

	@Test
	fun simpleRoute() {
		visitor {
			GET("/foo", ::handle)
		}.routes shouldBe listOf(
			Route(
				path = "/foo",
				methods = setOf(HttpMethod.GET),
				predicates = AndRoutePredicate(
					left = HttpMethodRoutePredicate(setOf(HttpMethod.GET)),
					right = PathPatternRoutePredicate("/foo")
				)
			)
		)
	}

	@Test
	fun simpleRoutePathPattern() {
		visitor {
			GET("/foo/{foo1}/bar/{bar2}", ::handle)
		}.routes shouldBe listOf(
			Route(
				path = "/foo/{foo1}/bar/{bar2}",
				methods = setOf(HttpMethod.GET),
				predicates = AndRoutePredicate(
					left = HttpMethodRoutePredicate(setOf(HttpMethod.GET)),
					right = PathPatternRoutePredicate("/foo/{foo1}/bar/{bar2}")
				)
			)
		)
	}

	@Test
	fun nestedQueryParam() {
		visitor {
			queryParam("bar1") { true }.nest {
				queryParam("bar2") { true }.nest {
					GET("/foo", ::handle)
				}
			}
		}.routes shouldBe listOf(
			Route(
				path = "/foo",
				methods = setOf(HttpMethod.GET),
				predicates = AndRoutePredicate(
					left = AndRoutePredicate(
						left = QueryParamRoutePredicate("bar1"),
						right = QueryParamRoutePredicate("bar2")
					),
					right = AndRoutePredicate(
						left = HttpMethodRoutePredicate(setOf(HttpMethod.GET)),
						right = PathPatternRoutePredicate("/foo")
					)
				)
			)
		)
	}

	@Test
	fun andOrRoutes() {
		visitor {
			(GET("/foo") or POST("/foos")) { req -> handle(req) }
		}.routes shouldBe listOf(
			Route(
				path = "/foo",
				methods = setOf(HttpMethod.GET),
				predicates = AndRoutePredicate(
					left = HttpMethodRoutePredicate(setOf(HttpMethod.GET)),
					right = PathPatternRoutePredicate("/foo")
				)
			),
			Route(
				path = "/foos",
				methods = setOf(HttpMethod.POST),
				predicates = AndRoutePredicate(
					left = HttpMethodRoutePredicate(setOf(HttpMethod.POST)),
					right = PathPatternRoutePredicate("/foos")
				)
			)
		)
	}

	@Test
	fun headerAndPath() {
		visitor {
			accept(MediaType.APPLICATION_JSON).and(path("bar1")).nest {
				HEAD("/foo", ::handle)
			}
		}.routes shouldBe listOf(
			Route(
				path = "/foo",
				methods = setOf(HttpMethod.HEAD),
				predicates = AndRoutePredicate(
					left = AndRoutePredicate(
						left = HeaderRoutePredicate("Accept: [application/json]"),
						right = PathPatternRoutePredicate("bar1")
					),
					right = AndRoutePredicate(
						left = HttpMethodRoutePredicate(setOf(HttpMethod.HEAD)),
						right = PathPatternRoutePredicate("/foo")
					)
				)
			)
		)
	}

	private infix fun <T> T.shouldBe(other: T) = Assert.assertEquals(other, this)
}
