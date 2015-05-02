package RayTracing;

import java.util.Random;

class RayTracingWorker implements Runnable {
    private Scene scene;
    private int imageWidth;
	private int bottomRow;
	private byte[] rgbData;
	private int topRow;

    RayTracingWorker(int bottomRow, int topRow, int imageWidth, Scene scene,
            byte[] rgbData) {
    	this.bottomRow = bottomRow;
        this.topRow = topRow;
    	this.scene = scene;
        this.imageWidth = imageWidth;
        this.rgbData = rgbData;
    }

    @Override
    public void run() {

        Color pixelColor;
        int progress = 0;

        for (int i = bottomRow; i < topRow; i++) {

        	int temp = (i*60)/imageWidth;
			if (temp>progress){
				progress = temp;
				System.out.print('.');
			}

        	for (int j = 0; j < imageWidth; j++) {
                pixelColor = getPixelColor(i, j);
                paintPixel(rgbData, i, j, pixelColor);
            }
        }
    }


    Color getPixelColor(int x, int y) {
		Ray ray = scene.camera.getRayByPixelCoordinate(x, y);
		return traceRay(ray, 0);
	}

	Color traceRay(Ray ray, int iteration) {
		Hit closestHit = getClosestHit(ray.moveOriginAlongRay(0.005));

		if (closestHit == null || iteration == scene.settings.maxRecursionLevel) {
			return scene.settings.background;
		}

		Color baseColor = Color.BLACK;
		for (Light light : scene.lights) {
			Ray shadowRay = Ray.createRayByTwoPoints(
				light.position,
				closestHit.intersection);

			double illumination = getIlluminationLevel(shadowRay, light, closestHit);
			double occlusion = 1 - illumination;
			double lightIntensity = 1-light.shadow;
			Color lightColor = light.color;
			Color diffuse = getDiffuse(closestHit, shadowRay).scale(illumination+occlusion*lightIntensity);
			Color specular = getSpecular(closestHit, shadowRay, light);

			baseColor = baseColor.add(diffuse.add(specular).multiply(lightColor));
		}
		
		//reflection
		Vector reflection = ray.dir.getReflectionAroundNormal(closestHit.normal);
		Color reflectionColor = Color.BLACK;
		if (!closestHit.getReflectColor().equals(Color.BLACK)){
			Ray reflectionRay = new Ray(closestHit.intersection, reflection);					
			reflectionColor = closestHit.getReflectColor().multiply(traceRay(reflectionRay, iteration + 1));
		}
		
		//transparency
		Color transparencyColor = Color.BLACK;
		float transparency = closestHit.getTransparency();
		float opacity = 1-transparency;
		if (transparency != 0) {
			Hit secondHit = getClosestHit(ray.moveOriginAlongRay(0.005), closestHit.shape);
			if (secondHit != null) {
				Ray transRay = new Ray(secondHit.intersection, ray.dir);
				transparencyColor = traceRay(transRay, iteration + 1);
			} else {
				transparencyColor = scene.settings.background;
			}
		}
		
		return transparencyColor.scale(transparency).add(baseColor.scale(opacity)).add(reflectionColor);
	}

	Hit getClosestHit(Ray ray, Shape3D ignore) {
		Hit closestHit = null;
		double minDist = Double.MAX_VALUE;

		for (Shape3D shape : scene.shapes) {
			Hit hit = shape.getHit(ray);

			if (hit != null && hit.dist < minDist) {
				if (ignore == null || ignore != hit.shape) {
					closestHit = hit;
					minDist = hit.dist;
				}
			}
		}

		return closestHit;
	}
	
	Hit getClosestHit(Ray ray){
		return getClosestHit(ray, null);
	}
	
	double getIlluminationLevel(Ray shadowRay, Light light, Hit hit){
		Vector[] grid = getLightGrid(shadowRay, light);
		int sum=0;
		for (int i=0; i<grid.length; i++){
			Ray ray = Ray.createRayByTwoPoints(grid[i], hit.intersection);
			if (!isOccluded(ray, hit))
				sum++;
		}
		return (double)sum/grid.length;
	}

	Vector[] getLightGrid(Ray ray, Light light){
		//construct rectangle
		Plane plane = ray.getPerpendicularPlaneAtOrigion();
		Vector edge1 = plane.getRandomDirection();
		Vector edge2 = edge1.cross(plane.normal);
		Vector vertex = Vector.sum(ray.p0, edge1.toLength(-light.width/2), edge2.toLength(-light.width/2));

		int shadowRaysNum = scene.settings.shadowRaysNum;
		Vector[] grid = new Vector[shadowRaysNum*shadowRaysNum];
		double tileWidth = light.width/shadowRaysNum;
		Random r = new Random();
		for (int i=0; i<shadowRaysNum; i++){
			for (int j=0; j<shadowRaysNum; j++){
				double alpha = tileWidth*(i+r.nextDouble());
				double beta = tileWidth*(j+r.nextDouble());
				grid[i*shadowRaysNum+j] = Vector.sum(vertex, edge1.toLength(alpha), edge2.toLength(beta));
			}
		}

		return grid;
	}

	boolean isOccluded(Ray shadowRay, Hit hit) {
		Hit closestHit = getClosestHit(shadowRay);

		if (closestHit == null)
			return true;
		if (closestHit.shape != hit.shape)
			return true;
		return closestHit.dist*closestHit.dist < hit.intersection.distSquared(shadowRay.p0)-0.000005;
	}

	Color getDiffuse(Hit hit, Ray shadowRay) {
		double cosOfAngle = hit.normal.getCosOfAngle(shadowRay.dir.reverse());

		if (cosOfAngle < 0)
			return Color.BLACK;
		return hit.getDiffuseColor().scale(cosOfAngle);
	}

	Color getSpecular(Hit hit, Ray shadowRay, Light light){
		Vector reflection = shadowRay.dir.getReflectionAroundNormal(hit.normal);
		Vector viewDirection = scene.camera.position.subtract(hit.intersection);
		double cosOfAngle = viewDirection.getCosOfAngle(reflection);

		if (cosOfAngle < 0 || hit.getSpecularColor().equals(Color.BLACK))
			return Color.BLACK;
		return hit.getSpecularColor().scale(light.spec * Math.pow(cosOfAngle, hit.getPhong()));
	}


	void paintPixel(byte[] rgbData, int x, int y, Color pixelColor) {
		rgbData[(y * this.imageWidth + x) * 3] = pixelColor.getRByte();
		rgbData[(y * this.imageWidth + x) * 3 + 1] = pixelColor.getGByte();
		rgbData[(y * this.imageWidth + x) * 3 + 2] = pixelColor.getBByte();
	}

}