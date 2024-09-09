import os

def collect_code_as_markdown(path, output_name="current_code.md", files_to_include=[], files_to_exclude=[]):
    """
    Collects all code files from a specified directory and writes them into a markdown file.
    Allows inclusion or exclusion of specific files based on provided lists.

    :param path: Path to the directory to search for files.
    :param files_to_include: List of specific file paths to include (only these files will be included if provided).
    :param files_to_exclude: List of specific file paths to exclude (these files will be excluded if provided).
    """
    # Normalize paths to be relative to the given path
    files_to_include = [os.path.join(path, file) for file in files_to_include] if files_to_include else []
    files_to_exclude = [os.path.join(path, file) for file in files_to_exclude] if files_to_exclude else []

    output_md_file = os.path.join(path, output_name)
    # print(files_to_include)

    files_included_count = 0

    if len(files_to_include) > 0:
        print(f"Only including {len(files_to_include)} files.")
        with open(output_md_file, 'w') as md_file:
            for file_path in files_to_include:
                files_included_count += 1
                file_extension = os.path.splitext(file_path)[1]
                md_file.write(f"## {os.path.basename(file_path)}\n")
                md_file.write(f"**Path:** `{file_path}`\n\n")
                md_file.write("```{}\n".format(file_extension[1:]))
                with open(file_path, 'r', encoding='utf-8') as code_file:
                    md_file.write(code_file.read())
                md_file.write("\n```\n\n")

    print(f"Markdown file created with {files_included_count} files: {output_md_file}")

    
    
    # files_included = 0
    # with open(output_md_file, 'w') as md_file:
        
    #     for root, dirs, files in os.walk(path):
    #         for file in files:
    #             file_path = os.path.join(root, file)
    #             file_extension = os.path.splitext(file_path)[1]
                
                
    #             supported_extensions = ['.scala']
    #             print(file, file in files_to_exclude)
                
    #             if files_to_include:
    #                 if file not in files_to_include:
    #                     continue  # Skip files not in the include list
    #             elif files_to_exclude:
    #                 if file in files_to_exclude:
    #                     continue  # Skip files in the exclude list

    #             # Check if the file should be included based on its extension
    #             if file_extension in supported_extensions or file_path in files_to_include:
    #                 # Write the filename and path as a header
    #                 md_file.write(f"## {file}\n")
    #                 md_file.write(f"**Path:** `{file_path}`\n\n")
    #                 md_file.write("```{}\n".format(file_extension[1:] if file_extension in supported_extensions else ''))
    #                 with open(file_path, 'r', encoding='utf-8') as code_file:
    #                     # print(f"Adding code from: {file_path}")
    #                     files_included += 1
    #                     md_file.write(code_file.read())
    #                 md_file.write("\n```\n\n")

    


# Example usage
path = "./"  # Path to the root directory you want to scan
output_name = "testing.md"
files_to_include = [
    "modules/data_l1/src/main/scala/com/my/water_and_energy_usage/data_l1/Main.scala",
    "modules/l0/src/main/scala/com/my/water_and_energy_usage/l0/Main.scala",
    "modules/l0/src/main/scala/com/my/water_and_energy_usage/l0/custom_routes/CustomRoutes.scala",
    "modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/calculated_state/CalculatedState.scala",
    "modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/calculated_state/CalculatedStateService.scala",
    "modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/combiners/Combiners.scala",
    "modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/deserializers/Deserializers.scala",
    "modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/errors/Errors.scala",
    "modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/serializers/Serializers.scala",
    "modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/types/Types.scala",
    "modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/validations/TypeValidators.scala",
    "modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/validations/Validations.scala",
    "modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/LifecycleSharedFunctions.scala",
    "modules/shared_data/src/main/scala/com/my/water_and_energy_usage/shared_data/Utils.scala"
]
files_to_exclude = None
# files_to_exclude = ['package.json', 
#                     'package-lock.json',
#                     'Dependencies.scala'
#                     ]  # Exclude these files if provided

# Call the function
collect_code_as_markdown(path, 
                         output_name=output_name,
                         files_to_include=files_to_include, files_to_exclude=files_to_exclude)