import java.sql.Array;
import java.util.*;

// The Map Itself
public class Map
{

    public double Length; // Length of the field in inches
    public double Breadth; // Breadth of the field in inches

    public List<MapObject> MapObjects = new ArrayList<>(); // List of MapObjects

    // Constructor for map
    public Map(double length, double breadth)
    {
        Length = length;
        Breadth = breadth;
    }

    // Add a circle object to the map
    public void CreateCircleObject(double xPos, double yPos, double radius, boolean isDrivable, String name)
    {
        MapObjects.add(new Circle(xPos, yPos, radius, isDrivable, name));
    }

    // Add a rect object to the map
    public void CreateRectObject(double xPos, double yPos, double length, double breadth, boolean isDrivable, String name)
    {
        MapObjects.add(new Rect(xPos, yPos, length, breadth, isDrivable, name));
    }

    // The rect object of the field itself
    public Rect WorldRect()
    {
        return new Rect(0, 0, Length, Breadth, true, "mapRect");
    }

    public static class LINE_FUNCTIONS
    {
        public static double[] GET_MIDPOINT(double[] startPoint, double[] endPoint) {
            double midX = (startPoint[0] + endPoint[0]) / 2;
            double midY = (startPoint[1] + endPoint[1]) / 2;
            return new double[]{midX, midY};
        }

        public static double GET_LENGTH(double[] startPoint, double[] endPoint) {
            return Math.sqrt((endPoint[0] - startPoint[0]) * (endPoint[0] - startPoint[0]) + (endPoint[1] - startPoint[1]) * (endPoint[1] - startPoint[1]));
        }

        public static boolean IS_VERTICAL(double[] startPoint, double[] endPoint) {
            return startPoint[0] == endPoint[0] && startPoint[1] != endPoint[1];
        }

        public static boolean IS_HORIZONTAL(double[] startPoint, double[] endPoint) {
            return startPoint[1] == endPoint[1] && startPoint[0] != endPoint[0];
        }

        public static double GET_GRADIENT(double[] startPoint, double[] endPoint) {
            if (IS_HORIZONTAL(startPoint, endPoint)) {
                return 0;
            } else if (IS_VERTICAL(startPoint, endPoint)) {
                throw new RuntimeException("Line is vertical");
            } else {
                return (endPoint[1] - startPoint[1]) / (endPoint[0] - startPoint[0]);
            }
        }

        public static double GET_YINTERCEPT(double gradient, double[] point) {
            return point[1] - gradient * point[0];
        }

        public static List<double[]> GET_WORLD_INTERSECTS(double gradient, double yIntercept, Rect worldRect) {
            double leftSide = -worldRect.Breadth / 2;
            double rightSide = worldRect.Breadth / 2;
            double topSide = worldRect.Length / 2;
            double bottomSide = -worldRect.Length / 2;
            List<double[]> worldIntersects =  worldRect.checkLinearIntersect(gradient, yIntercept, leftSide-1, bottomSide-1, rightSide+1, topSide+1, 0);
            return worldIntersects;
        }

        public static List<double[]> GET_WORLD_INTERSECTS(boolean isVertical, double posOnAxis, Rect worldRect) {
            List<double[]> values = new ArrayList<>();
            if (isVertical) {
                values.add(new double[]{posOnAxis, worldRect.Length / 2});
                values.add(new double[]{posOnAxis, -worldRect.Length / 2});
            } else {
                values.add(new double[]{worldRect.Breadth / 2, posOnAxis});
                values.add(new double[]{-worldRect.Breadth / 2, posOnAxis});
            }
            return values;
        }

        public static List<double[]> GET_PERPENDICULAR_BISECTOR(double[] startPoint, double[] endPoint, Rect worldRect) {
            double[] midPoint = GET_MIDPOINT(startPoint, endPoint);

            if (IS_VERTICAL(startPoint, endPoint)) {
                return GET_WORLD_INTERSECTS(false, midPoint[1], worldRect);
            } else if (IS_HORIZONTAL(startPoint, endPoint)) {
                return GET_WORLD_INTERSECTS(true, midPoint[0], worldRect);
            } else {
                double gradient = GET_GRADIENT(startPoint, endPoint);
                List<double[]> bisectLine = GET_WORLD_INTERSECTS(-1/gradient, GET_YINTERCEPT(-1 / gradient, midPoint), worldRect);
                if (bisectLine.size() != 0)
                {
                    return bisectLine;
                }else
                {
                    throw new RuntimeException("Invalid points " + startPoint[0] + " " + startPoint[1] + "|" + endPoint[0] + " " + endPoint[1]);
                }
            }
        }

        public static List<double[]> ORDER_INTERSECTS(List<double[]> intersects, double[] startPoint)
        {
            intersects.sort((double[] intersect1, double[] intersect2) -> (int) (GET_LENGTH(startPoint, intersect1) - GET_LENGTH(startPoint, intersect2)));
            return intersects;
        }

        public static double GET_PATH_LENGTH (List<double[]> path)
        {
            double totalLength = 0;
            for (int i = 0; i < path.size()-2; i++)
            {
                totalLength += GET_LENGTH(path.get(i), path.get(i+1));
            }
            return totalLength;
        }

    }

    // Group shapes whose buffers intersect into hybrid shapes
    public void GroupShapes(double buffer)
    {
        List<MapObject> groupedMapObjects = new ArrayList<>();
        ListIterator<MapObject> mapObjects = MapObjects.listIterator();
        while(mapObjects.hasNext())
        {
            MapObject object = mapObjects.next();
            List<MapObject> compObject = new ArrayList<>();
            compObject.add(object);
            mapObjects.remove();
            HybridObject group = new HybridObject(compObject);
            while (mapObjects.hasNext())
            {
                MapObject otherObject = mapObjects.next();
                if (group.Overlaps(otherObject, buffer))
                {
                    group.AddObj(otherObject);
                    mapObjects.remove();
                }
            }
            if (group.CompObjects.size() == 1)
            {
                groupedMapObjects.add(object);
            }else
            {
                groupedMapObjects.add(group);
            }
        }
        MapObjects = groupedMapObjects;
    }

    // Short shapes according to the reverse of their order of intersection with the line
    public void OrderShapes(double[] startPoint, double[] endPoint)
    {
        MapObjects.sort((MapObject obj1, MapObject obj2) -> (int)(obj1.PosOnLine(startPoint, endPoint) - obj2.PosOnLine(startPoint, endPoint)));
        Collections.reverse(MapObjects);
    }


    // Fully clear path for all shapes
    public List<double[]> ClearPath(double[] startPoint, double[] endPoint, double buffer)
    {
        // Group shapes that are near one another into hybrid objects
        GroupShapes(buffer);

        // Order shapes according to proximity to end point
        OrderShapes(startPoint, endPoint);

        // Initialize path for robot given start point and end point
        List<double[]> path = new ArrayList<>(List.of(startPoint, endPoint));

        // Clear path for each shape
        for (int i = 0; i < MapObjects.size(); i++)
        {
            path = FindValidPath(i, path, buffer);
        }

        // Return the final path
        return path;
    }

    // Try both directions to find the better path
    public List<double[]> FindValidPath(int shapeIndex, List<double[]> currentPath, double buffer)
    {
        // Find both directions around the shape
        List<double[]> pathOne = ClearShapePath(shapeIndex, currentPath, buffer, false);
        List<double[]> pathTwo = ClearShapePath(shapeIndex, currentPath, buffer, true);

        // Return the shorter path if it isn't null, otherwise return the other path. If they are both null, throw an exception
        if (pathOne.size() == 0)
        {
            if (pathTwo.size() > 0)
            {
                System.out.println("pathTwo");
                return pathTwo;
            }
        }else if(pathTwo.size() == 0){
            System.out.println("pathOne");
            return pathOne;
        }else{
            System.out.println("oneOrTwo");
            double pathOneLength = LINE_FUNCTIONS.GET_PATH_LENGTH(pathOne);
            double pathTwoLength = LINE_FUNCTIONS.GET_PATH_LENGTH(pathTwo);
            if (pathOneLength > pathTwoLength)
            {
                return pathTwo;
            }else{
                return pathOne;
            }
        }
        throw new Error("No valid path");
    }

    public List<double[]> ClearShapePath(int shapeIndex, List<double[]> currentPath, double buffer, boolean longerPath)
    {

        // Initialize the new path in terms of a sequence of points
        List<double[]> newPath = new ArrayList<>();


        // For each line segment on the path
        for (int i = 0; i < currentPath.size()-1; i++)
        {
            // Initialize a variable to store found intersects on the path
            List<double[]> intersectPoints = new ArrayList<>();
            // Find its intersects with the shape
            intersectPoints.addAll(MapObjects.get(shapeIndex).checkIntersect(currentPath.get(i), currentPath.get(i+1), 0));

            // If it intersects twice, execute the recursive loop, bisecting the line and then clearing that path
            if (intersectPoints.size() == 2 && intersectPoints.get(0) != intersectPoints.get(1)) {

                List<double[]> bisectedSegment = BisectAndExtendOut(shapeIndex,  intersectPoints, currentPath.get(i), currentPath.get(i+1), buffer, longerPath);
                // Conditions to return null value upwards if error occurs in bisecting (due to path going outside map)
                if (bisectedSegment.size() != 0)
                {
                    List<double[]> newSegment = ClearShapePath(shapeIndex, bisectedSegment, buffer, false);
                    if (newSegment.size() != 0)
                    {
                        newPath.addAll(newSegment.subList(0, newSegment.size() - 1));
                    }else
                    {
                        return new ArrayList<>();
                    }
                }else{
                    return new ArrayList<>();
                }
            } else // If it doesn't intersect then just add the line segment as it was to the new path
            {
                newPath.add(currentPath.get(i));
            }

        }
        newPath.add(currentPath.get(currentPath.size()-1));

        return newPath; // Return the new path when finished
    }

    public List<double[]> BisectAndExtendOut(int shapeIndex, List<double[]> intersectPoints, double[] startPoint, double[] endPoint, double buffer, boolean longerPath)
    {
        // Find the perpendicular bisector of the intersects
        List<double[]> perpendicular = LINE_FUNCTIONS.GET_PERPENDICULAR_BISECTOR(intersectPoints.get(0), intersectPoints.get(1), WorldRect());
        // Find new points by finding the bisectors intersects with the cushioned shape
        List<double[]> newPoints = (MapObjects.get(shapeIndex).checkIntersect(perpendicular.get(0), perpendicular.get(1), buffer));

        // Find the first possible path
        List<double[]> pathOne = new ArrayList<>();
        pathOne.add(startPoint);
        pathOne.add(newPoints.get(0));
        pathOne.add(endPoint);

        // Find the second possible path
        List<double[]> pathTwo = new ArrayList<>();
        pathTwo.add(startPoint);
        pathTwo.add(newPoints.get(1));
        pathTwo.add(endPoint);

        // Return the requested path whether longer or shorter
        if ((LINE_FUNCTIONS.GET_PATH_LENGTH(pathOne) >= LINE_FUNCTIONS.GET_PATH_LENGTH(pathTwo)) == longerPath)
        {
            // Make sure the new point is in the map, else start returning null upwards
            if (WorldRect().IsInside(pathOne.get(1)))
            {
                return pathOne;
            }else{
                return new ArrayList<>();
            }
        }else
        {
            // Make sure the new point is in the map, else start returning null upwards
            if (WorldRect().IsInside(pathTwo.get(1)))
            {
                return pathTwo;
            }else{
                return new ArrayList<>();
            }
        }
    }

    // An entity on the map
    public class MapObject
    {
        // x and y position
        double XPos = 0;
        double YPos = 0;

        // Whether the object can be driven over. Can be toggled for different paths
        boolean IsDrivable = false;

        // The name of the object for easy reference
        String Name = "";

        // References to the functions in the subclasses
        public List<double[]> checkVerticalIntersect(double xPos, double minY, double maxY, double buffer){return null;}
        public List<double[]> checkLinearIntersect(double gradient, double yIntercept, double minX, double minY, double maxX, double maxY, double buffer){return null;}
        public List<double[]> checkHorizontalIntersect(double yPos, double minX, double maxX, double buffer){return null;}
        public List<double[]> checkIntersect(double[] startPos, double[] endPos, double buffer){return null;}
        public boolean IsInside(double[] position){return false;}
        public boolean Overlaps(MapObject object, double buffer){return false;}
        public double PosOnLine(double[] startPoint, double[] endPoint){return 0;}
    }

    // Abstract object on map
    public class RandObj extends MapObject
    {
        // The vertices of the object defined as vector2s
        List<double[]> Vertices;
    }

    // Circle Object
    public class Circle extends MapObject
    {

        public double Radius;

        // Circle constructor
        public Circle(double xPos, double yPos, double radius, boolean isDrivable, String name)
        {
            // Set the x position
            super.XPos = xPos;
            // Set the y position
            super.YPos = yPos;
            // Set the radius
            Radius = radius;
            // Set whether the object is drivable
            super.IsDrivable = isDrivable;
            // Set the name of the object
            super.Name = name;
        }

        // Check whether slanted line intersects with shape
        public List<double[]> checkLinearIntersect(double gradient, double yIntercept, double minX, double minY, double maxX, double maxY, double buffer)
        {

            // Initialize list to store roots
            List<double[]> ans = new ArrayList<>();
            // The radius with cushioning taken into account
            double bufferRad = Radius + buffer;
            // The co-efficient of x squared
            double a = gradient * gradient + 1;
            // The co-efficient of x the sum of the two co-efficients in the two brackets
            double b = (2 * gradient * (yIntercept - super.YPos)) -(2 * super.XPos);
            // The real numbers added together
            double c = (yIntercept - super.YPos)*(yIntercept - super.YPos) + (super.XPos)*(super.XPos) - bufferRad*bufferRad;
            // Determinant using quadratic formula
            double determinant = b*b - 4*a*c;
            // If there is only one root
            if (determinant == 0)
            {
                // Quadratic formula without determinant
                double rootX = (-b)/2*a;
                double rootY = gradient * rootX + yIntercept;
                if (rootX > minX && rootX < maxX && rootY > minY && rootY < maxY)
                {
                    ans.add(new double[]{rootX, rootY});
                }
                // If there are two roots
            }else if (determinant > 0)
            {
                // Quadratic formula with determinant for first root
                double root1X = (-b + Math.sqrt(determinant))/(2*a);

                // Y-Value gotten using line function
                double root1Y = gradient * root1X + yIntercept;

                // Check if root is within range
                if (root1X > minX && root1X < maxX && root1Y > minY && root1Y < maxY)
                {
                    ans.add(new double[]{root1X, root1Y});
                }

                // Quadratic formula with determinant for second root
                double root2X = (-b - Math.sqrt(determinant))/(2*a);

                // Y-value gotten using line function
                double root2Y = root2X * gradient + yIntercept;
                // Check if root is within range
                if (root2X > minX && root2X < maxX && root2Y > minY && root2Y < maxY)
                {
                    ans.add(new double[]{root2X, root2Y});
                }
            }


            return ans;
        }

        // Check whether vertical line intersects with shape
        public List<double[]> checkVerticalIntersect(double xPos, double minY, double maxY, double buffer)
        {
            // The radius with cushioning taken into account
            double bufferRad = buffer + Radius;
            // Initialize list to store roots
            List<double[]> ans = new ArrayList<>();

            // Check whether the line crosses the circle
            if ((maxY > super.YPos + bufferRad && minY < super.YPos + bufferRad) || (maxY > super.YPos - bufferRad && minY < super.YPos - bufferRad) || (xPos > super.XPos - bufferRad || xPos < super.XPos + bufferRad))
            {
                // The co-efficient of y squared
                double a = 1;
                // The co-efficient of y the sum of the two co-efficients in the two brackets
                double b = -2 * super.YPos;
                // The real numbers added together
                double c = super.YPos * super.YPos + (xPos - super.XPos)*(xPos - super.XPos) - bufferRad*bufferRad;
                // Determinant using quadratic formula
                double determinant = b*b - 4*a*c;


                // If there is only one root
                if (determinant == 0)
                {
                    // Quadratic formula without determinant
                    double rootY = (-b)/(2*a);

                    // Check if root is within range
                    if (rootY > minY && rootY < maxY)
                    {
                        ans.add(new double[]{xPos, rootY});
                    }
                    // If there are two roots
                }else if (determinant > 0)
                {
                    // Quadratic formula with determinant for first root
                    double root1Y = (-b + Math.sqrt(determinant))/(2*a);

                    // Check if root is within range
                    if (root1Y > minY && root1Y < maxY)
                    {
                        ans.add(new double[]{xPos, root1Y});
                    }
                    // Quadratic formula with determinant for second root
                    double root2Y = (-b - Math.sqrt(determinant))/(2*a);

                    // Check if root is within range
                    if (root2Y > minY && root2Y < maxY)
                    {
                        ans.add(new double[]{xPos, root2Y});
                    }

                }
            }
            return ans;
        }

        public List<double[]> checkHorizontalIntersect(double yPos, double minX, double maxX, double buffer)
        {
            // The radius with cushioning taken into account
            double bufferRad = buffer + Radius;
            // Initialize list to store roots
            List<double[]> ans = new ArrayList<>();
            // Check whether the line crosses the circle
            if ((maxX > super.XPos + bufferRad && minX < super.XPos + bufferRad) || (maxX > super.XPos - bufferRad && minX < super.XPos - bufferRad) || (yPos > super.YPos - bufferRad || yPos < super.YPos + bufferRad))
            {
                // The co-efficient of x squared
                double a = 1;
                // The co-efficient of x the sum of the two co-efficients in the two brackets
                double b = -2 * super.XPos;
                // The real numbers added together
                double c = super.XPos*super.XPos + (yPos - super.YPos)*(yPos - super.YPos) - bufferRad*bufferRad;
                // Determinant using quadratic formula
                double determinant = b*b - 4*a*c;
                // If there is only one root
                if (determinant == 0)
                {
                    // X-Value gotten using line function
                    double rootX = (-b)/(2*a);

                    // Check if root is within range
                    if (rootX > minX && rootX < maxX)
                    {
                        ans.add(new double[]{rootX, yPos});
                    }
                    // If there are two roots
                }else if (determinant > 0)
                {
                    // X-Value gotten using line function
                    double root1X = (-b + Math.sqrt(determinant))/(2*a);

                    // Check if root is within range
                    if (root1X > minX && root1X < maxX)
                    {
                        ans.add(new double[]{root1X, yPos});
                    }

                    double root2X = (-b - Math.sqrt(determinant))/(2*a);

                    // Check if root is within range
                    if (root2X > minX && root2X < maxX)
                    {
                        ans.add(new double[]{root2X, yPos});
                    }

                }
            }
            return ans;
        }

        public List<double[]> checkIntersect(double[] startPoint, double[] endPoint, double buffer)
        {

            if (LINE_FUNCTIONS.IS_VERTICAL(startPoint, endPoint)) // If there is no change in x do vertical intersect
            {
                // Change in y position
                double yDiff =  endPoint[1] - startPoint[1];
                // Account for negative change in y or positive change in y
                if (yDiff < 0)
                {
                    return checkVerticalIntersect(startPoint[0], endPoint[1], startPoint[1], buffer);
                }else if (yDiff > 0)
                {
                    return LINE_FUNCTIONS.ORDER_INTERSECTS(checkVerticalIntersect(startPoint[0], startPoint[1], endPoint[1], buffer), startPoint);
                }
                // If there is no motion return null
                return new ArrayList<>();
            }else if (LINE_FUNCTIONS.IS_HORIZONTAL(startPoint, endPoint)) // If there is no change in y do vertical intersect
            {
                // Change in x position
                double xDiff =  endPoint[0] - startPoint[0];
                // Account for negative change in x or positive change in x
                if (xDiff < 0)
                {
                    return checkHorizontalIntersect(startPoint[1], endPoint[0], startPoint[0], buffer);
                }else if (xDiff > 0)
                {
                    return LINE_FUNCTIONS.ORDER_INTERSECTS(checkHorizontalIntersect(startPoint[1], startPoint[0], endPoint[0], buffer), startPoint);
                }
                // If there is no motion return null
                return new ArrayList<>();
            }else // Otherwise, do linear intersect
            {

                // Set minimum x
                double minX = Math.min(startPoint[0], endPoint[0]);
                // Set maximum x
                double maxX = Math.max(startPoint[0], endPoint[0]);
                // Set minimum y
                double minY = Math.min(startPoint[1], endPoint[1]);
                // Set maximum y
                double maxY = Math.max(startPoint[1], endPoint[1]);
                // Set the gradient of the given line
                double gradient = LINE_FUNCTIONS.GET_GRADIENT(startPoint, endPoint);
                // Set the y-Intercept of the given line
                double yIntercept = LINE_FUNCTIONS.GET_YINTERCEPT(gradient, startPoint);
                return LINE_FUNCTIONS.ORDER_INTERSECTS(checkLinearIntersect(gradient, yIntercept, minX, minY, maxX, maxY, buffer), startPoint);
            }
        }

        // Whether the point is within the shape
        public boolean IsInside(double[] position)
        {
            return (position[0]*position[0] + position[1]*position[1] < Radius*Radius);
        }

        // Whether the shape overlaps with another map object
        public boolean Overlaps(MapObject object, double buffer)
        {
            if (object.getClass() == Circle.class)
            {
                Circle circle = (Circle)object;
                return LINE_FUNCTIONS.GET_LENGTH(new double[] {super.XPos, super.YPos}, new double[] {object.XPos, object.YPos}) < (Radius + circle.Radius);
            }else if (object.getClass() == Rect.class)
            {
                Rect rect = (Rect)object;
                List<double[]> rectVertices = rect.GetVertices();
                for (int i = 0; i < rectVertices.size()-1; i++)
                {
                    if (checkIntersect(rectVertices.get(i), rectVertices.get(i+1), buffer).size() > 0)
                    {
                        return true;
                    }
                }
                if (checkIntersect(rectVertices.get(3), rectVertices.get(0), buffer).size() > 0)
                {
                    return true;
                }

            }else if (object.getClass() == HybridObject.class)
            {
                return object.Overlaps(this, buffer);
            }
            return false;
        }

        // The length along a line at which it first crosses the circle
        public double PosOnLine(double[] startPoint, double[] endPoint)
        {
            List<double[]> intersects = checkIntersect(startPoint, endPoint, 0);
            if (intersects.size() > 0)
            {
                return LINE_FUNCTIONS.GET_LENGTH(intersects.get(0), startPoint);
            }else{
                return LINE_FUNCTIONS.GET_LENGTH(startPoint, endPoint);
            }
        }
    }

    public class Rect extends MapObject
    {
        // Length of rectangle
        public double Length;
        // Breadth of rectangle
        public double Breadth;

        public Rect(double xPos, double yPos, double length, double breadth, boolean isDrivable, String name)
        {
            // Set the x position
            super.XPos = xPos;
            // Set the y position
            super.YPos = yPos;
            // Set the length of the rectangle
            Length = length;
            // Set the breadth of the rectangle
            Breadth = breadth;
            // Set whether the object can be driven over
            super.IsDrivable = isDrivable;
            // Set the name of the object
            super.Name = name;
        }

        public List<double[]> checkLinearIntersect(double gradient, double yIntercept, double minX, double minY, double maxX, double maxY, double buffer)
        {
            // Initialize variable to store results
            List<double[]> ans = new ArrayList<>();
            // Set the x coordinates for the left side of the rect
            double leftSide = super.XPos - (Breadth/2 + buffer);
            // Set the x coordinates for the right side of the rect
            double rightSide = super.XPos + (Breadth/2 + buffer);
            // Set the y coordinates for the bottom side of the rect
            double bottomSide = super.YPos - (Length/2 + buffer);
            // Set the y coordinates for the top side of the rect
            double topSide = super.YPos + (Length/2 + buffer);
            // Find where the line intersects
            double y1 = gradient * leftSide + yIntercept;
            // Verify that it is in the range
            if(y1 > minY && y1 < maxY && y1 >= bottomSide && y1 <= topSide)
            {
                // Add the answer
                ans.add(new double[]{leftSide, y1});
            }
            // Set the x coordinates for the right side of the rect
            // Find where the line intersects
            double y2 = gradient * rightSide + yIntercept;
            // Verify that it is in the range
            if (y2 > minY && y2 < maxY && y2 >= bottomSide && y2 <= topSide)
            {
                // Add the answer
                ans.add(new double[]{rightSide, y2});
            }
            // Check that this y isn't already recorded as a corner intersect
            if (y1 != bottomSide && y2 != bottomSide)
            {

                // Find where the line intersects
                double x1 = (bottomSide - yIntercept)/gradient;
                // Verify that it is in the range
                if (x1 > minX && x1 < maxX && x1 >= leftSide && x1 <= rightSide)
                {
                    // Add the answer
                    ans.add(new double[]{x1, bottomSide});
                }
            }
            // Check that this y isn't already recorded as a corner intersect
            if (y2 != topSide && y1 != topSide)
            {
                // Find where the line intersects
                double x2 = (topSide - yIntercept)/gradient;
                // Verify that it is in the range
                if (x2 > minX && x2 < maxX && x2 >= leftSide && x2 <= rightSide)
                {
                    // Add the answer
                    ans.add(new double[]{x2, topSide});
                }
            }
            return ans;
        }

        public List<double[]> checkVerticalIntersect(double xPos, double minY, double maxY, double buffer)
        {
            // Initialize variable to store results
            List<double[]> ans = new ArrayList<>();
            // Top Side
            double topSide = super.YPos + (Length/2 + buffer);
            // Bottom side
            double bottomSide = super.YPos - (Length/2 + buffer);
            // Whether the line intersects with the top side
            boolean crossesTop = (maxY > topSide && minY < topSide);
            // Whether the line intersects with the bottom side
            boolean crossesBottom = (maxY > bottomSide && minY < bottomSide);
            // Whether they exist in the same x range
            boolean existsInRange = (xPos < super.XPos + (Breadth/2 + buffer) && xPos > super.XPos - (Breadth/2 + buffer));
            if (existsInRange)
            {
                if (crossesTop)
                {
                    // Add the answer
                    ans.add(new double[]{xPos, topSide});
                }
                if (crossesBottom)
                {
                    // Add the answer
                    ans.add(new double[]{xPos, bottomSide});
                }
            }
            return ans;
        }

        public List<double[]> checkHorizontalIntersect(double yPos, double minX, double maxX, double buffer)
        {
            // Initialize variable to store results
            List<double[]> ans = new ArrayList<>();
            // Top Side
            double rightSide = super.XPos + (Breadth/2 + buffer);
            // Bottom side
            double leftSide = super.XPos - (Breadth/2 + buffer);
            // Whether the line intersects with the right side
            boolean crossesRight = (maxX > rightSide && minX < rightSide);
            // Whether the line intersects with the bottom side
            boolean crossesLeft = (maxX > leftSide && minX < leftSide);
            // Whether they exist in the same y range
            boolean existsInRange = (yPos < super.YPos + (Length/2 + buffer) && yPos > super.YPos - (Length/2 + buffer));
            if (existsInRange)
            {
                if (crossesRight)
                {
                    // Add the answer
                    ans.add(new double[]{rightSide, yPos});
                }
                if (crossesLeft)
                {
                    // Add the answer
                    ans.add(new double[]{leftSide, yPos});
                }
            }
            return ans;
        }

        public List<double[]> checkIntersect(double[] startPoint, double[] endPoint, double buffer)
        {

            if (LINE_FUNCTIONS.IS_VERTICAL(startPoint, endPoint)) // If there is no change in x do vertical intersect
            {
                // Change in y position
                double yDiff =  endPoint[1] - startPoint[1]; // Change in y position
                // Account for negative change in y or positive change in y
                if (yDiff < 0)
                {
                    return checkVerticalIntersect(startPoint[0], endPoint[1], startPoint[1], buffer);
                }else if (yDiff > 0)
                {
                    return LINE_FUNCTIONS.ORDER_INTERSECTS(checkVerticalIntersect(startPoint[0], startPoint[1], endPoint[1], buffer), startPoint);
                }
                // If there is no motion return null
                return new ArrayList<>();
            }else if (LINE_FUNCTIONS.IS_HORIZONTAL(startPoint, endPoint)) // If there is no change in y do vertical intersect
            {
                // Change in x position
                double xDiff =  endPoint[0] - startPoint[0];

                // Account for negative change in x or positive change in x
                if (xDiff < 0)
                {
                    return checkHorizontalIntersect(startPoint[1], endPoint[0], startPoint[0], buffer);
                }else if (xDiff > 0)
                {
                    return LINE_FUNCTIONS.ORDER_INTERSECTS(checkHorizontalIntersect(startPoint[1], startPoint[0], endPoint[0], buffer), startPoint);
                }
                // If there is no motion return null
                return new ArrayList<>();
            }else // Otherwise, do linear intersect
            {

                // Set minimum x
                double minX = Math.min(startPoint[0], endPoint[0]);
                // Set maximum x
                double maxX = Math.max(startPoint[0], endPoint[0]);
                // Set minimum y
                double minY = Math.min(startPoint[1], endPoint[1]);
                // Set maximum y
                double maxY = Math.max(startPoint[1], endPoint[1]);
                // Set the gradient of the given line
                double gradient = LINE_FUNCTIONS.GET_GRADIENT(startPoint, endPoint);
                // Set the y-Intercept of the given line
                double yIntercept = LINE_FUNCTIONS.GET_YINTERCEPT(gradient, startPoint);
                return LINE_FUNCTIONS.ORDER_INTERSECTS(checkLinearIntersect(gradient, yIntercept, minX, minY, maxX, maxY, buffer), startPoint);
            }


        }

        public boolean IsInside(double[] position)
        {
            // Set the x coordinates for the left side of the rect
            double leftSide = super.XPos - Breadth/2;
            // Set the x coordinates for the right side of the rect
            double rightSide = super.XPos + Breadth/2;
            // Set the y coordinates for the bottom side of the rect
            double bottomSide = super.YPos - Length/2;
            // Set the y coordinates for the top side of the rect
            double topSide = super.YPos + Length/2;

            return (position[0] > leftSide &&  position[0] < rightSide && position[1] > bottomSide && position[1] < topSide);
        }

        // Returns all the vertices of the rect
        public List<double[]> GetVertices()
        {
            double[] topRight = new double[] {Breadth/2 + super.XPos, Length/2 + super.YPos};
            double[] topLeft = new double[] {-Breadth/2 + super.XPos, Length/2 + super.YPos};
            double[] bottomRight = new double[] {Breadth/2 + super.XPos, -Length/2 + super.YPos};
            double[] bottomLeft = new double[] {-Breadth/2 + super.XPos, -Length/2 + super.YPos};
            List<double[]> vertices = new ArrayList<>();
            vertices.add(topRight);
            vertices.add(topLeft);
            vertices.add(bottomRight);
            vertices.add(bottomLeft);
            return vertices;
        }

        // Whether the rect overlaps with another shape
        public boolean Overlaps(MapObject object, double buffer)
        {
            if (object.getClass()== Circle.class)
            {;
                return object.Overlaps(this, buffer);
            }else if (object.getClass() == Rect.class)
            {
                Rect rect = (Rect)object;
                List<double[]> rectVertices = rect.GetVertices();
                for (int i = 0; i < rectVertices.size()-1; i++)
                {
                    if (checkIntersect(rectVertices.get(i), rectVertices.get(i+1), buffer).size() > 0)
                    {
                        return true;
                    }
                }
                if (checkIntersect(rectVertices.get(3), rectVertices.get(0), buffer).size() > 0)
                {
                    return true;
                }

            }else if (object.getClass() == HybridObject.class)
            {
                return object.Overlaps(this, buffer);
            }
            return false;
        }

        // The length across a line at which it first crosses the shape
        public double PosOnLine(double[] startPoint, double[] endPoint)
        {
            List<double[]> intersects = checkIntersect(startPoint, endPoint, 0);
            if (intersects.size() > 0)
            {
                return LINE_FUNCTIONS.GET_LENGTH(intersects.get(0), startPoint);
            }else{
                return LINE_FUNCTIONS.GET_LENGTH(startPoint, endPoint);
            }
        }
    }

    public class HybridObject extends MapObject
    {

        // List of the composite shapes that make up the hybrid shape
        public List<MapObject> CompObjects;

        // Constructor for the hybrid object
        public HybridObject(List<MapObject> compObjects)
        {
            CompObjects = compObjects;
            compObjects.stream().forEach(obj -> super.Name += obj.Name);
        }

        public void AddObj(MapObject obj)
        {
            super.Name += obj.Name;
            CompObjects.add(obj);
        }

        // Check if line crosses the hybrid object
        public List<double[]> checkIntersect(double[] startPoint, double[] endPoint, double buffer)
        {
            List<double[]> intersects = new ArrayList<>();
            // For each of the composite shapes
            for (MapObject compObject : CompObjects) {

                // Check where the line intersects them
                List<double[]> compositeIntersect = compObject.checkIntersect(startPoint, endPoint, buffer);
                // For each of the intersects found
                for (double[] intersect : compositeIntersect) {
                    // Ensure that it is not inside the hybrid object
                    intersects.add(intersect);
                }

            }
            // Sort the intersects according to their position along the line
            LINE_FUNCTIONS.ORDER_INTERSECTS(intersects, startPoint);

            // Consider only the extremes
            if (intersects.size() > 0)
            {
                List<double[]> extremeIntersects = new ArrayList<>();
                extremeIntersects.add(intersects.get(0));
                extremeIntersects.add(intersects.get(intersects.size() - 1));
                return extremeIntersects;
            }else
            {
                return new ArrayList<>();
            }
            // Return the found intersects

        }

        // Check if a point is inside the hybrid object
        public boolean IsInside(double[] point)
        {
            for (MapObject compObject : CompObjects) {
                if (compObject.IsInside(point)) {
                    return true;
                }
            }
            return false;
        }

        // Check if the object overlaps with any other object
        public boolean Overlaps(MapObject object, double buffer)
        {
            for (MapObject compObject : CompObjects) {
                if (compObject.Overlaps(object, buffer)) {
                    return true;
                }
            }
            return false;
        }

        // The length across a line at which it first crosses the shape
        public double PosOnLine(double[] startPoint, double[] endPoint)
        {
            List<double[]> intersects = checkIntersect(startPoint, endPoint, 0);
            if (intersects.size() > 0)
            {
                return LINE_FUNCTIONS.GET_LENGTH(intersects.get(0), startPoint);
            }else{
                return LINE_FUNCTIONS.GET_LENGTH(startPoint, endPoint);
            }
        }
    }

    public static void main(String[] args)
    {
        Map myMap = new Map(20, 20);
        myMap.CreateCircleObject(0, 6, 7.5, false, "Circle");
        myMap.CreateRectObject(-6.5, 6, 8, 5, false, "Rect");
        double[] startPos = new double[] {-10, 6.5};
        double[] endPos = new double[] {10, 6.5};
        List<double[]> path = myMap.ClearPath(startPos, endPos,0.001);
        System.out.println("Path length " + path.size());
        for (double[] point: path)
        {
            System.out.println(point[0] + ", " + point[1]);
        }

    }



}
