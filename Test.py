import sys

# Increase recursion depth just in case N is large, 
# though standard 1000 is usually enough for N=100
sys.setrecursionlimit(2000)

def calculate_metric(numbers, index, current_sum):
    """
    Recursively calculates the sum based on the rules.
    """
    # Base case: we have processed all numbers
    if index == len(numbers):
        return current_sum
    
    val = int(numbers[index])
    
    # RULE IMPLEMENTATION: 
    # "Calculate the power of four of Yn, excluding when Yn is positive"
    # This means we only calculate if Yn <= 0.
    if val <= 0:
        # If the problem actually asks for sum of squares, change `** 4` to `** 2`
        # and remove the `if val <= 0` check if it requires all numbers.
        term = val ** 4
        current_sum = current_sum + term
        
    return calculate_metric(numbers, index + 1, current_sum)

def solve_test_case(current_case, total_cases, results):
    """
    Recursively processes each test case.
    """
    # Base case: we have processed all test cases
    if current_case == total_cases:
        return results

    # Read X (Expected count)
    line_x = sys.stdin.readline()
    if not line_x:
        return results
    
    try:
        x = int(line_x.strip())
    except ValueError:
        x = 0

    # Read Y (The list of integers)
    line_y = sys.stdin.readline()
    if not line_y:
        y_list = []
    else:
        # split() is allowed (it's a string method, not a comprehension)
        y_list = line_y.strip().split()

    # VALIDATION: Check if number of integers matches X
    if len(y_list) != x:
        results.append(-1)
    else:
        # Calculate recursively
        answ = calculate_metric(y_list, 0, 0)
        results.append(answ)

    return solve_test_case(current_case + 1, total_cases, results)

def print_results(results, index):
    """
    Recursively prints the results to standard output.
    """
    if index == len(results):
        return
    
    print(results[index])
    print_results(results, index + 1)

def main():
    # Read Number of Test Cases (N)
    line = sys.stdin.readline()
    if not line:
        return
        
    try:
        n = int(line.strip())
    except ValueError:
        return

    # Solve and store results
    final_results = solve_test_case(0, n, [])
    
    # Print all results at the end
    print_results(final_results, 0)

if __name__ == "__main__":
    main()