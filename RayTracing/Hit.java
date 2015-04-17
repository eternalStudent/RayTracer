package RayTracing;

public class Hit {
	final double dist;
	final Shape3D shape;
	final Ray ray;
	final Vector normal;
	final Vector intersection;

	Hit(Shape3D shape, double dist, Ray ray) {
		this.dist = dist;
		this.shape = shape;
		this.ray = ray;

		intersection = ray.getVectAlongRay(dist);
		normal = shape.getNormalAtSurfacePoint(intersection);
	}

	RGB getDiffuse() {
		return shape.material.diffuse;
	}

	RGB getSpecular() {
		return shape.material.specular;
	}

	float getPhong() {
		return shape.material.phong;
	}
}