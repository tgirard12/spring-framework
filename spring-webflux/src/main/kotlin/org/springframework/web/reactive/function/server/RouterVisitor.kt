package org.springframework.web.reactive.function.server

import org.springframework.core.io.Resource
import org.springframework.http.HttpMethod
import reactor.core.publisher.Mono
import java.util.function.Function

data class Route(
	val path: String,
	val methods: Set<HttpMethod>,
	val predicates: RoutePredicate
)

interface RoutePredicate {
	val type: String
}

interface PairRoutePredicate : RoutePredicate {
	val left: RoutePredicate
	val right: RoutePredicate
}

fun RoutePredicate.nest(routePredicate: RoutePredicate) =
	if (this == EMPTY) routePredicate
	else AndRoutePredicate(this, routePredicate)

fun RoutePredicate.unNest() = when (this) {
	is AndRoutePredicate -> this.left
	is OrRoutePredicate -> this.left
	else -> EMPTY
}

data class QueryParamRoutePredicate(
	val name: String
) : RoutePredicate {
	override val type: String = "QueryParam"
}

data class HeaderRoutePredicate(
	val name: String
) : RoutePredicate {
	override val type: String = "Header"
}

data class HttpMethodRoutePredicate(
	val httpMethods: Set<HttpMethod>
) : RoutePredicate {
	override val type: String = "HttpMethod"
}

data class PathPatternRoutePredicate(
	val path: String
) : RoutePredicate {
	override val type: String = "PathPattern"
}

data class PathExtensionRoutePredicate(
	val name: String
) : RoutePredicate {
	override val type: String = "PathPathExtension"
}

data class AndRoutePredicate(
	override val left: RoutePredicate,
	override val right: RoutePredicate
) : PairRoutePredicate {
	override val type: String = "AndPredicate"
}

data class OrRoutePredicate(
	override val left: RoutePredicate,
	override val right: RoutePredicate
) : PairRoutePredicate {
	override val type: String = "OrPredicate"
}

object EMPTY : RoutePredicate {
	override val type: String = "EMPTY"
}

internal fun RequestPredicates.QueryParamPredicate.toRoutePredicate() = QueryParamRoutePredicate(this.name)
internal fun RequestPredicates.HeadersPredicate.toRoutePredicate() = HeaderRoutePredicate(this.toString())
internal fun RequestPredicates.HttpMethodPredicate.toRoutePredicate() = HttpMethodRoutePredicate(this.httpMethods)

internal fun RequestPredicates.PathPatternPredicate.toRoutePredicate() =
	PathPatternRoutePredicate(this.pattern.patternString)

internal fun RequestPredicates.PathExtensionPredicate.toRoutePredicate() =
	PathExtensionRoutePredicate(this.extensionPredicate.toString())

internal fun RequestPredicates.AndRequestPredicate.toRoutePredicate() = AndRoutePredicate(
	left = this.left.toRoutePredicate(),
	right = this.right.toRoutePredicate()
)

internal fun RequestPredicates.OrRequestPredicate.toRoutePredicate() = OrRoutePredicate(
	left = this.left.toRoutePredicate(),
	right = this.right.toRoutePredicate()
)

internal fun RequestPredicate.toRoutePredicate(): RoutePredicate = when (this) {
	is RequestPredicates.QueryParamPredicate -> this.toRoutePredicate()
	is RequestPredicates.HeadersPredicate -> this.toRoutePredicate()
	is RequestPredicates.HttpMethodPredicate -> this.toRoutePredicate()

	is RequestPredicates.PathPatternPredicate -> this.toRoutePredicate()
	is RequestPredicates.PathExtensionPredicate -> this.toRoutePredicate()

	is RequestPredicates.AndRequestPredicate -> this.toRoutePredicate()
	is RequestPredicates.OrRequestPredicate -> this.toRoutePredicate()

	else -> TODO("RequestPredicates ${this::class} Unknow")
}

internal fun RequestPredicate.toPairRoutePredicate(): PairRoutePredicate = when (this) {
	is RequestPredicates.AndRequestPredicate -> this.toRoutePredicate()
	is RequestPredicates.OrRequestPredicate -> this.toRoutePredicate()

	else -> TODO("RequestPredicates ${this::class} Unknow")
}


class RouteVisitor
	: RouterFunctions.Visitor {

	var currentPredicate: RoutePredicate = EMPTY

	val routes = mutableListOf<Route>()

	override fun startNested(predicate: RequestPredicate) {
		currentPredicate = currentPredicate.nest(predicate.toRoutePredicate())
	}

	override fun endNested(predicate: RequestPredicate) {
		currentPredicate = currentPredicate.unNest()
	}

	override fun route(predicate: RequestPredicate, handlerFunction: HandlerFunction<*>) {

		val routePredicate = predicate.toPairRoutePredicate()
		routePredicate.addRoute()

		var pairRoute = routePredicate.left
		while (pairRoute is PairRoutePredicate) {
			pairRoute.addRoute()
			pairRoute = pairRoute.left
		}
		pairRoute = routePredicate.right
		while (pairRoute is PairRoutePredicate) {
			pairRoute.addRoute()
			pairRoute = pairRoute.right
		}
	}

	private fun PairRoutePredicate.addRoute() {
		val method = left as? HttpMethodRoutePredicate
		val path = right as? PathPatternRoutePredicate

		if (method != null && path != null) {
			routes.add(
				Route(
					path = path.path, methods = method.httpMethods,
					predicates = currentPredicate.nest(this)
				)
			)
		}
	}

	override fun resources(lookupFunction: Function<ServerRequest, Mono<Resource>>) {
	}

	override fun unknown(routerFunction: RouterFunction<*>) {
	}
}
